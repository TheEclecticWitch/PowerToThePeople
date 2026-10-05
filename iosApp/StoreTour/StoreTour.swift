import XCTest

/// Walks through the app on the simulator for the App Store screenshots and preview video.
/// Run by tools/store-shots.sh; TOUR_OUT is the folder on the Mac that the pictures go to.
final class StoreTour: XCTestCase {
    let app = XCUIApplication()
    lazy var out = ProcessInfo.processInfo.environment["TOUR_OUT"] ?? NSTemporaryDirectory()

    override func setUp() {
        continueAfterFailure = false
    }

    /// The address used in every store picture: Independence Hall, so nobody's home shows.
    func setLocation() {
        if !app.buttons["Set my location"].waitForExistence(timeout: 20) { return }  // already set
        app.buttons["Set my location"].tap()
        let field = app.textViews["Street address, city, state, ZIP"]
        XCTAssert(field.waitForExistence(timeout: 10))
        field.tap()
        field.typeText("520 Chestnut St, Philadelphia, PA 19106")
        app.buttons["Find my districts"].tap()
        let done = app.buttons["See who represents me"]
        XCTAssert(done.waitForExistence(timeout: 60))
        done.tap()
    }

    func shot(_ name: String) {
        let png = XCUIScreen.main.screenshot().pngRepresentation
        try? png.write(to: URL(fileURLWithPath: out + "/" + name + ".png"))
    }

    func dump(_ name: String) {
        try? app.debugDescription.write(toFile: out + "/" + name + ".txt", atomically: true, encoding: .utf8)
    }

    func wait(_ label: String, _ seconds: Double = 30) -> XCUIElement {
        let e = app.descendants(matching: .any)[label]
        XCTAssert(e.waitForExistence(timeout: seconds), "never saw \(label)")
        return e
    }

    /// Drags the page so the element's top sits at y (in points), slowly enough that it doesn't coast.
    func scroll(_ e: XCUIElement, toY y: CGFloat = 110) {
        for _ in 0..<12 {
            let top = e.frame.minY
            if abs(top - y) < 8 { return }
            let step = max(min(top - y, 600), -600)
            let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0)).withOffset(CGVector(dx: 0, dy: 760))
            let end = start.withOffset(CGVector(dx: 0, dy: -step))
            start.press(forDuration: 0.05, thenDragTo: end, withVelocity: 300, thenHoldForDuration: 0.4)
        }
    }

    func first(_ type: XCUIElement.ElementType, _ format: String) -> XCUIElement {
        let e = app.descendants(matching: type).matching(NSPredicate(format: format)).firstMatch
        XCTAssert(e.waitForExistence(timeout: 30), "never saw \(format)")
        return e
    }

    /// Drags the page back down to its top: a page only lists what is near the screen.
    func toTop() {
        for _ in 0..<10 where !app.staticTexts["Our Democratic Republic"].exists {
            app.swipeDown(velocity: .fast)
        }
        sleep(2)  // let the swipe coast to a stop before dragging exactly
        scroll(app.staticTexts["Our Democratic Republic"], toY: 90)
        sleep(1)
    }

    var isPad: Bool { app.windows.firstMatch.frame.width > 700 }

    /// The iPad keyboard isn't in the app's own element tree, so its bottom-right key, which puts it
    /// away, is tapped by position. A phone's keyboard closes by itself when a page opens.
    func hideKeyboard() {
        guard isPad else { return }
        sleep(1)
        let w = app.windows.firstMatch.frame
        app.coordinate(withNormalizedOffset: CGVector(dx: 0, dy: 0))
            .withOffset(CGVector(dx: w.width - 32, dy: w.height - 22)).tap()
        sleep(1)
    }

    /// On an iPad most pages open beside their list, so there may be no Back to tap.
    func back() {
        let b = app.buttons["Back"].firstMatch
        if b.exists && b.isHittable { b.tap() }
        sleep(1)
    }

    /// The App Store screenshots, in the order they appear on the store page.
    func testShots() throws {
        app.launch()
        setLocation()
        _ = wait("Fetterman")
        sleep(4)  // the portraits
        shot("1-overview")

        first(.button, "label BEGINSWITH 'Photo of' AND label ENDSWITH 'Representative'").tap()
        sleep(5)
        shot("2-member")
        back()

        scroll(wait("THIS WEEK IN CONGRESS"), toY: 100)
        sleep(2)
        if !isPad { shot("6-this-week") }  // an iPad shows it on the first page already
        first(.button, "label CONTAINS 'roll call'").tap()
        sleep(4)
        shot("3-vote")
        back()
        toTop()

        app.buttons["See who's running ›"].tap()
        sleep(5)
        if isPad {  // fill the right-hand pane with the candidates
            first(.button, "label CONTAINS 'See the candidates'").tap()
            sleep(4)
        }
        shot("5-elections")
        back()

        app.buttons["More"].tap()
        sleep(2)
        first(.button, "label BEGINSWITH[c] 'Legislation'").tap()
        sleep(3)
        let search = app.textViews.firstMatch
        search.tap()
        search.typeText("S. 2403\n")
        first(.button, "label BEGINSWITH 'S. 2403'").tap()
        sleep(6)
        hideKeyboard()
        shot("4-bill")

        app.buttons["Civics"].tap()
        sleep(3)
        shot("7-civics")
        if isPad {  // the practice test, in place of This Week
            app.buttons["Citizenship test"].tap()
            sleep(3)
            shot("6-citizenship-test")
        }
        app.buttons["Constitution"].tap()
        sleep(4)
        if isPad {
            first(.button, "label CONTAINS 'The Legislative Branch'").tap()
            sleep(3)
        }
        shot("8-constitution")
    }

    /// Glides the page up by the given points, at reading speed, the way a person scrolls.
    func glide(_ points: CGFloat, seconds: Double = 1.2) {
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0)).withOffset(CGVector(dx: 0, dy: 760))
        start.press(forDuration: 0.05, thenDragTo: start.withOffset(CGVector(dx: 0, dy: -points)),
                    withVelocity: XCUIGestureVelocity(points / seconds), thenHoldForDuration: 0.2)
    }

    func pause(_ seconds: Double) { Thread.sleep(forTimeInterval: seconds) }

    /// The walk-through for the App Preview video: opening the app and its main features, at a pace
    /// a viewer can follow. tools/store-shots.sh records the screen while it runs.
    func testVideo() throws {
        app.launch()
        setLocation()
        _ = wait("Fetterman")
        app.terminate()
        pause(2)
        XCUIDevice.shared.press(.home)
        pause(2)
        try "".write(toFile: out + "/start", atomically: true, encoding: .utf8)  // the video starts here
        pause(1.5)
        app.launch()
        _ = wait("Fetterman")
        pause(3.5)

        // Your officials, then a member's page.
        first(.button, "label BEGINSWITH 'Photo of' AND label ENDSWITH 'Representative'").tap()
        pause(2.5)
        glide(420)
        pause(2)
        back()
        pause(1)

        // How they voted.
        scroll(wait("THIS WEEK IN CONGRESS"), toY: 100)
        pause(2)
        first(.button, "label CONTAINS 'roll call'").tap()
        pause(2.5)
        app.buttons["Yea"].firstMatch.tap()
        pause(2)
        back()
        toTop()

        // Elections.
        app.buttons["See who's running ›"].tap()
        pause(3)
        glide(380)
        pause(2)
        back()
        pause(1)

        // Any bill in Congress.
        app.buttons["More"].tap()
        pause(1.5)
        first(.button, "label BEGINSWITH[c] 'Legislation'").tap()
        pause(1.5)
        let search = app.textViews.firstMatch
        search.tap()
        search.typeText("S. 2403\n")
        pause(1)
        first(.button, "label BEGINSWITH 'S. 2403'").tap()
        pause(3)
        glide(400)
        pause(2)

        // Civics and the Constitution.
        app.buttons["Civics"].tap()
        pause(3)
        app.buttons["Citizenship test"].tap()
        pause(2.5)
        app.buttons["Constitution"].tap()
        pause(3)
        try "".write(toFile: out + "/end", atomically: true, encoding: .utf8)
    }
}
