-- One row per (roll call, answering install). `voter` is a one-way hash of the install's random code,
-- the roll call and a server secret, so the same install looks unrelated on every vote: the table holds
-- no names, no addresses, no IP addresses, and no way to follow one person's answers across votes.
CREATE TABLE IF NOT EXISTS answers (
  vote   TEXT NOT NULL,          -- "119/senate/2/256": congress/chamber/session/roll
  voter  TEXT NOT NULL,
  answer TEXT NOT NULL CHECK (answer IN ('Yea', 'Nay')),
  PRIMARY KEY (vote, voter)
);

-- Running totals, so reading a count never has to add up every answer.
CREATE TABLE IF NOT EXISTS totals (
  vote TEXT PRIMARY KEY,
  yea  INTEGER NOT NULL DEFAULT 0,
  nay  INTEGER NOT NULL DEFAULT 0
);
