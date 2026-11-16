export function formatClock(totalSeconds) {
  const safe = Math.max(0, Math.floor(totalSeconds));
  const minutes = String(Math.floor(safe / 60)).padStart(2, "0");
  const seconds = String(safe % 60).padStart(2, "0");

  return `${minutes}:${seconds}`;
}

export function percentage(part, total) {
  return total ? Math.round((part / total) * 100) : 0;
}

// history: [{ tag, correct }] → strongest and weakest tags.
export function summarizeAreas(history) {
  const byTag = {};

  history.forEach(({ tag, correct }) => {
    byTag[tag] = byTag[tag] || { tag, correct: 0, total: 0 };
    byTag[tag].total += 1;
    if (correct) byTag[tag].correct += 1;
  });

  const tags = Object.values(byTag).map((item) => ({
    ...item,
    accuracy: item.correct / item.total
  }));

  const strong = tags
    .filter((item) => item.correct > 0)
    .sort((a, b) => b.accuracy - a.accuracy || b.correct - a.correct)[0];

  const weak = tags
    .filter((item) => item.correct < item.total)
    .sort((a, b) => a.accuracy - b.accuracy || b.total - a.total)[0];

  return {
    strongArea: strong ? strong.tag : "Keep practising",
    weakArea: weak ? weak.tag : "None in this session"
  };
}
