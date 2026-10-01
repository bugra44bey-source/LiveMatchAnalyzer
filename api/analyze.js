export default async function handler(req, res) {
  if (req.method !== "GET") {
    return res.status(405).json({ error: "Method not allowed" });
  }

  const key = process.env.API_FOOTBALL_KEY;
  const fixture = req.query?.fixture;

  if (!key) {
    return res.status(500).json({ error: "API_FOOTBALL_KEY is not configured" });
  }

  if (!fixture) {
    return res.status(400).json({ error: "fixture query parameter is required" });
  }

  const headers = { "x-apisports-key": key };

  try {
    const [predictionResponse, statisticsResponse] = await Promise.all([
      fetch(
        "https://v3.football.api-sports.io/predictions?fixture=" +
          encodeURIComponent(fixture),
        { headers }
      ),
      fetch(
        "https://v3.football.api-sports.io/fixtures/statistics?fixture=" +
          encodeURIComponent(fixture),
        { headers }
      )
    ]);

    const predictionData = await predictionResponse.json();
    const statisticsData = await statisticsResponse.json();

    return res.status(200).json({
      fixture: String(fixture),
      prediction: predictionData.response ?? [],
      statistics: statisticsData.response ?? []
    });
  } catch (error) {
    return res.status(500).json({
      error: "Fixture analysis request failed",
      message: error instanceof Error ? error.message : String(error)
    });
  }
}
