// The Microsoft Store side of donations, for the Windows app. Copied from Granny's Oldtime Cookbook
// and changed for consumables: a donation can be given again and again.
//
// The app runs on the JVM, which cannot call the Store's Windows Runtime API, so this tiny program
// does it and answers on standard output. It is packed inside the MSIX beside the app, so it runs
// with the app's package identity - which is what the Store looks at. Built with the C# compiler
// every Windows has (.NET Framework 4), by tools/windows-store/build_helper.py.
//
//   StoreHelper.exe prices <store id> [<store id> ...]  ->  price.<store id>=<formatted price> per add-on found
//   StoreHelper.exe buy <store id>                      ->  result=bought|cancelled|failed  (or error=<why>)
//
// Each donation is a "Developer-managed consumable" add-on in Partner Center. The Store refuses to
// sell one again until the app reports it fulfilled, so "buy" does that straight after paying. If an
// earlier payment was never reported (the app closed mid-way), the Store answers "already purchased":
// that payment is reported now and counts as the donation, so the person is thanked, not charged twice.
using System;
using System.Runtime.InteropServices;
using System.Threading;
using Windows.Foundation;
using Windows.Services.Store;

static class StoreHelper
{
    static int Main(string[] args)
    {
        try
        {
            if (args.Length < 2) { Console.WriteLine("error=usage: prices|buy <add-on store id>..."); return 2; }
            return Run(args);
        }
        catch (Exception e)
        {
            Console.WriteLine("error=" + e.Message.Replace('\r', ' ').Replace('\n', ' '));
            return 1;
        }
    }

    static int Run(string[] args)
    {
        StoreContext context = StoreContext.GetDefault();
        // The purchase window needs an owner: the app's window, which is in front when a button is clicked.
        IntPtr owner = GetForegroundWindow();
        if (owner != IntPtr.Zero)
        {
            ((IInitializeWithWindow)(object)context).Initialize(owner);
        }

        if (args[0] == "prices")
        {
            string[] ids = new string[args.Length - 1];
            Array.Copy(args, 1, ids, 0, ids.Length);
            // "Consumable" is developer-managed, "UnmanagedConsumable" Store-managed; either is found.
            StoreProductQueryResult found = Wait(context.GetStoreProductsAsync(new[] { "Consumable", "UnmanagedConsumable" }, ids));
            if (found.ExtendedError != null) Console.WriteLine("error=" + found.ExtendedError.Message.Replace('\r', ' ').Replace('\n', ' '));
            foreach (string id in ids)
            {
                StoreProduct product;
                if (found.Products.TryGetValue(id, out product))
                {
                    Console.WriteLine("price." + id + "=" + product.Price.FormattedPrice);
                }
            }
            return 0;
        }

        if (args[0] == "buy")
        {
            string storeId = args[1];
            StorePurchaseResult result = Wait(context.RequestPurchaseAsync(storeId));
            switch (result.Status)
            {
                case StorePurchaseStatus.Succeeded:
                case StorePurchaseStatus.AlreadyPurchased:
                    // Paid (now, or earlier and never reported): tell the Store it's done so it can be given again.
                    StoreConsumableResult done = Wait(context.ReportConsumableFulfillmentAsync(storeId, 1, Guid.NewGuid()));
                    if (done.Status != StoreConsumableStatus.Succeeded)
                    {
                        Console.WriteLine("fulfilled=false");
                    }
                    Console.WriteLine("result=bought");
                    break;
                case StorePurchaseStatus.NotPurchased:
                    Console.WriteLine("result=cancelled");
                    break;
                default:
                    Console.WriteLine("result=failed");
                    if (result.ExtendedError != null) Console.WriteLine("error=" + result.ExtendedError.Message.Replace('\r', ' ').Replace('\n', ' '));
                    break;
            }
            return 0;
        }

        Console.WriteLine("error=unknown command " + args[0]);
        return 2;
    }

    /**
     * Waits for a Windows Runtime answer. The usual "await" needs a bridge library that only comes
     * with the .NET SDK; asking the operation how it is getting on needs nothing.
     */
    static T Wait<T>(IAsyncOperation<T> op)
    {
        while (op.Status == AsyncStatus.Started) Thread.Sleep(50);
        if (op.Status == AsyncStatus.Error) throw new Exception(op.ErrorCode != null ? op.ErrorCode.Message : "The Store did not answer.");
        if (op.Status == AsyncStatus.Canceled) throw new Exception("The Store request was cancelled.");
        return op.GetResults();
    }

    [DllImport("user32.dll")]
    static extern IntPtr GetForegroundWindow();

    [ComImport]
    [Guid("3E68D4BD-7135-4D10-8018-9FB6D9F33FA1")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IInitializeWithWindow
    {
        void Initialize(IntPtr hwnd);
    }
}
