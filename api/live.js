module.exports = async function handler(req, res) {
  if (req.method !== "GET") {
    return res.status(405).json({ error: "Method not allowed" });
  }

  const key = process.env.API_FOOTBALL_KEY;
  if (!key) {
    return res.status(500).json({ error: "API_FOOTBALL_KEY is not configured" });
  }

  try {
    const mode = req.query?.mode || "live";
    const today = new Intl.DateTimeFormat("en-CA", {
      timeZone: "Europe/Istanbul"
    }).format(new Date());

    const endpoint = mode === "today"
      ? `https://v3.football.api-sports.io/fixtures?date=${today}&timezone=Europe%2FIstanbul`
      : "https://v3.football.api-sports.io/fixtures?live=all";

    const response = await fetch(endpoint, {
      headers: { "x-apisports-key": key }
    });
    const data = await response.json();

    if (!response.ok) {
      return res.status(response.status).json({
        error: "API-Football request failed",
        details: data
      });
    }

    return res.status(200).json({
      results: data.results ?? 0,
      fixtures: data.response ?? []
    });
  } catch (error) {
    return res.status(500).json({
      error: "Fixtures request failed",
      message: error instanceof Error ? error.message : String(error)
    });
  }
};
