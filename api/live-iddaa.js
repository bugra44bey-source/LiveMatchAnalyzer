module.exports = async function handler(req, res) {
  if (req.method !== "GET") {
    return res.status(405).json({ error: "Method not allowed" });
  }

  const key = process.env.API_FOOTBALL_KEY;
  if (!key) {
    return res.status(500).json({ error: "API_FOOTBALL_KEY is not configured" });
  }

  try {
    const headers = { "x-apisports-key": key };

    const [iddaaResponse, footballResponse] = await Promise.all([
      fetch("https://sportsbookv2.iddaa.com/sportsbook/events?st=1&type=0&version=0"),
      fetch("https://v3.football.api-sports.io/fixtures?live=all", { headers })
    ]);

    const iddaaData = await iddaaResponse.json();
    const footballData = await footballResponse.json();

    if (!iddaaResponse.ok) {
      return res.status(iddaaResponse.status).json({
        error: "iddaa live request failed",
        details: iddaaData
      });
    }

    if (!footballResponse.ok) {
      return res.status(footballResponse.status).json({
        error: "API-Football live request failed",
        details: footballData
      });
    }

    const iddaaEvents = iddaaData?.data?.events ?? [];
    const liveFixtures = footballData?.response ?? [];

    const normalize = (value) =>
      String(value || "")
        .toLowerCase()
        .normalize("NFD")
        .replace(/[\u0300-\u036f]/g, "")
        .replace(/[^a-z0-9]+/g, " ")
        .trim();

    const words = (value) => new Set(normalize(value).split(/\s+/).filter(Boolean));

    const score = (a, b) => {
      const na = normalize(a);
      const nb = normalize(b);
      if (!na || !nb) return 0;
      if (na === nb) return 1;
      if (na.includes(nb) || nb.includes(na)) return 0.9;
      const wa = words(a);
      const wb = words(b);
      let common = 0;
      for (const w of wa) if (wb.has(w)) common++;
      return common / Math.max(wa.size, wb.size, 1);
    };

    const matchScore = (iddaaEvent, fixture) => {
      const home = fixture?.teams?.home?.name || "";
      const away = fixture?.teams?.away?.name || "";
      const homeScore = score(iddaaEvent.hn, home);
      const awayScore = score(iddaaEvent.an, away);
      return (homeScore + awayScore) / 2;
    };

    const matched = [];
    for (const event of iddaaEvents) {
      let best = null;
      let bestScore = 0;

      for (const fixture of liveFixtures) {
        const s = matchScore(event, fixture);
        if (s > bestScore) {
          bestScore = s;
          best = fixture;
        }
      }

      if (best && bestScore >= 0.55) {
        matched.push(best);
      }
    }

    const unique = [];
    const seen = new Set();
    for (const fixture of matched) {
      const id = fixture?.fixture?.id;
      if (!id || seen.has(id)) continue;
      seen.add(id);
      unique.push(fixture);
    }

    return res.status(200).json({
      source: "iddaa",
      results: unique.length,
      fixtures: unique
    });
  } catch (error) {
    return res.status(500).json({
      error: "iddaa live request failed",
      message: error instanceof Error ? error.message : String(error)
    });
  }
};
