# Print and PDF Export Guide

## Open the report

Open [PROJECT_REPORT.html](PROJECT_REPORT.html) directly in **Google Chrome** or **Microsoft Edge**. Both browsers use the Chromium print engine targeted by this layout.

The report references assets already present in this repository. Keep the repository folder structure unchanged while previewing or printing so the logo and screenshots resolve correctly.

## Recommended PDF settings

1. Open the HTML file in Chrome or Edge.
2. Select **Print** and choose **Save to PDF**.
3. Use these settings:

| Setting | Recommended value |
| --- | --- |
| Destination | Save to PDF |
| Paper size | A4 |
| Pages | All |
| Layout | Portrait |
| Scale | 100% |
| Margins | Default |
| Background graphics | **Enabled** |
| Headers and footers | Disabled |

4. Preview every page before saving. The print stylesheet supplies A4 margins, page-break guidance, table header repetition, and image sizing.

> [!TIP]
> Load the report once while connected to the internet before printing if you want the optional Google web fonts. The CSS has professional local fallbacks, so the report remains readable without them.

## Scope

This folder is a publication layer only. It does not change the Spring Boot application, frontend behavior, source Markdown report, tests, or API documentation.
