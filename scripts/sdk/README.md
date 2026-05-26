# SDK generation scripts

This folder contains helper scripts for generating API client SDKs from Freerouting OpenAPI.

## Scripts

- `generate-python-client.ps1`
- `generate-javascript-client.ps1`
- `generate-csharp-client.ps1`
- `generate-cpp-client.ps1`
- `regenerate-all.ps1`

The language-specific generator scripts:

- use `https://api.freerouting.app/openapi/openapi.json` by default,
- support a local OpenAPI file path via `-OpenApiSource`,
- run OpenAPI Generator through Docker,
- support `-TemplateDir` for custom templates,
- support `-DryRun` for previewing the exact generator command.

`regenerate-all.ps1` runs all four generators in sequence and forwards shared settings (OpenAPI source, output root, versions, dry-run mode).

## Quick usage

```powershell
./scripts/sdk/generate-python-client.ps1 -OutputDir build/sdk/python-client -PackageVersion 0.2.0
./scripts/sdk/generate-javascript-client.ps1 -OutputDir build/sdk/javascript-client -NpmVersion 0.2.0
./scripts/sdk/generate-csharp-client.ps1 -OutputDir build/sdk/csharp-client -PackageVersion 0.2.0 -TargetFramework net8.0
./scripts/sdk/generate-cpp-client.ps1 -OutputDir build/sdk/cpp-client -PackageVersion 0.2.0
./scripts/sdk/regenerate-all.ps1 -SharedVersion 0.2.0
```

## Dry run examples

```powershell
./scripts/sdk/generate-python-client.ps1 -DryRun
./scripts/sdk/generate-javascript-client.ps1 -DryRun
./scripts/sdk/generate-csharp-client.ps1 -DryRun
./scripts/sdk/generate-cpp-client.ps1 -DryRun
./scripts/sdk/regenerate-all.ps1 -SharedVersion 0.2.0 -DryRun
```

## Notes

- The generated output is intended for SDK repositories (for example `freerouting-python-client`, future `freerouting-js-client`, `freerouting-csharp-client`, and `freerouting-cpp-client`).
- Keep language-specific CI, packaging, and release publishing in each SDK repository.