import express from "express";

const app = express();
app.use(express.json({ limit: "1mb" }));

const PORT = Number(process.env.PORT || 3000);
const API_KEY = process.env.AI_API_KEY;
const API_URL = process.env.AI_API_URL || "https://api.openai.com/v1/chat/completions";
const MODEL = process.env.AI_MODEL || "gpt-4o-mini";

app.get("/health", (_req, res) => {
  res.json({ status: "ok", service: "NEXORA" });
});

app.post("/chat", async (req, res) => {
  try {
    const message = typeof req.body?.message === "string" ? req.body.message.trim() : "";
    if (!message) {
      return res.status(400).json({ error: "Message is required." });
    }
    if (!API_KEY) {
      return res.status(503).json({ error: "AI service is not configured yet. Add AI_API_KEY in the hosting environment." });
    }

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 45000);

    let upstream;
    try {
      upstream = await fetch(API_URL, {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${API_KEY}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          model: MODEL,
          messages: [{ role: "user", content: message }]
        }),
        signal: controller.signal
      });
    } finally {
      clearTimeout(timeout);
    }

    const data = await upstream.json().catch(() => ({}));
    if (!upstream.ok) {
      console.error("AI provider error:", upstream.status, data?.error?.message || "Unknown error");
      return res.status(502).json({ error: "The AI provider could not complete the request. Check the server configuration." });
    }

    const reply = data?.choices?.[0]?.message?.content;
    if (typeof reply !== "string" || !reply.trim()) {
      return res.status(502).json({ error: "The AI provider returned an empty response." });
    }

    return res.json({ reply: reply.trim() });
  } catch (error) {
    console.error("Chat request failed:", error?.name || "Error");
    return res.status(500).json({ error: "NEXORA could not process the request. Please try again." });
  }
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`NEXORA backend listening on port ${PORT}`);
});
