/**
 * Single source of truth for the "Universal EDA Compatibility" grid on index.html.
 * To add an integration: drop <id>.svg into assets/logos/eda/ and append one entry here.
 * No other file needs to change.
 *
 * logoStyle: "mono"  - official mark rendered as a white silhouette (CSS filter),
 *                      used when the full-color mark lacks contrast on the dark card.
 *            "brand" - full-color mark (used for the in-repo gold generic glyph).
 */
const EDA_INTEGRATIONS = [
  {
    id: "kicad",
    name: "KiCad",
    logoStyle: "mono",
    description:
      "Full bidirectional workflow via KiCad's Specctra export/import and dedicated community KiCad plugins.",
    url: null,
  },
  {
    id: "autodesk-fusion",
    name: "Autodesk Fusion",
    logoStyle: "mono",
    description:
      "Native integration and automated routing workflows for Autodesk Fusion PCB design.",
    url: null,
  },
  {
    id: "easyeda",
    name: "EasyEDA",
    logoStyle: "mono",
    description:
      "Direct support for automated trace generation via Specctra DSN export from standard web and desktop clients.",
    url: null,
  },
  {
    id: "target3001",
    name: "Target 3001!",
    logoStyle: "mono",
    description:
      "Seamless automated routing pipeline with Target 3001! PCB design software.",
    url: null,
  },
  {
    id: "pcb-rnd",
    name: "pcb-rnd",
    logoStyle: "mono",
    description: "Direct support for pcb-rnd automated layout and routing workflows.",
    url: null,
  },
  {
    id: "specctra",
    name: "Specctra DSN & SES",
    logoStyle: "brand",
    description:
      'Full compliance with industry-standard .dsn design files and .ses routing session output.',
    url: null,
  },
];

function renderEdaCards() {
  const grid = document.querySelector("#integrations .eda-grid");
  if (!grid) return;
  grid.innerHTML = EDA_INTEGRATIONS.map(
    (item) => `
      <div class="eda-card">
        <div class="eda-header">
          <img class="eda-logo eda-logo--${item.logoStyle}"
               src="assets/logos/eda/${item.id}.svg"
               alt="${item.name} logo" width="26" height="26" loading="lazy">
          <h3>${item.name}</h3>
        </div>
        <p>${item.description}</p>
      </div>`
  ).join("");
}

document.addEventListener("DOMContentLoaded", renderEdaCards);
