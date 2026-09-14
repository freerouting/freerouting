/**
 * Single source of truth for the "Universal EDA Compatibility" grid on index.html.
 * To add an integration: drop <id>.svg into assets/logos/eda/ and append one entry here.
 * No other file needs to change.
 *
 * Schema: id (kebab-case, maps to assets/logos/eda/<id>.svg),
 *         name (card title), logoStyle ("brand" | "mono"),
 *         description (card body), url (optional vendor/product site; null = no link).
 *
 * logoStyle: "brand" - full-color mark rendered with a subtle drop-shadow lift
 *                      (used when the mark has adequate contrast on the dark card).
 *            "mono"  - mark rendered as a white silhouette via CSS filter
 *                      (used when the full-color mark lacks contrast on the dark card).
 */
const EDA_INTEGRATIONS = [
  {
    id: "kicad",
    name: "KiCad",
    logoStyle: "brand",
    description:
      "Full bidirectional workflow via KiCad's Specctra export/import and dedicated community KiCad plugins.",
    url: "https://www.kicad.org/",
  },
  {
    id: "autodesk-fusion",
    name: "Autodesk Fusion",
    logoStyle: "brand",
    description:
      "Native integration and automated routing workflows for Autodesk Fusion PCB design.",
    url: "https://www.autodesk.com/products/fusion-360/overview",
  },
  {
    id: "easyeda",
    name: "EasyEDA",
    logoStyle: "brand",
    description:
      "Direct support for automated trace generation via Specctra DSN export from standard web and desktop clients.",
    url: "https://easyeda.com/",
  },
  {
    id: "target3001",
    name: "Target 3001!",
    logoStyle: "mono",
    description:
      "Seamless automated routing pipeline with Target 3001! PCB design software.",
    url: "https://ibfriedrich.com/",
  },
  {
    id: "pcb-rnd",
    name: "pcb-rnd",
    logoStyle: "mono",
    description: "Direct support for pcb-rnd automated layout and routing workflows.",
    url: "http://www.repo.hu/projects/pcb-rnd",
  },
  {
    id: "specctra",
    name: "Specctra DSN & SES",
    logoStyle: "brand",
    description:
      "Full compliance with industry-standard .dsn design files and .ses routing session output.",
    url: null,
  },
];

function renderEdaCards() {
  const grid = document.querySelector("#integrations .eda-grid");
  if (!grid) return;
  grid.innerHTML = EDA_INTEGRATIONS.map((item) => {
    const title = item.url
      ? `<a class="eda-link" href="${item.url}" target="_blank" rel="noopener noreferrer">${item.name}</a>`
      : item.name;
    return `
      <div class="eda-card">
        <div class="eda-header">
          <img class="eda-logo eda-logo--${item.logoStyle}"
               src="assets/logos/eda/${item.id}.svg"
               alt="${item.name} compatibility icon" width="28" height="28" loading="lazy">
          <h3>${title}</h3>
        </div>
        <p>${item.description}</p>
      </div>`;
  }).join("");
}

document.addEventListener("DOMContentLoaded", renderEdaCards);
