# Module ops config — see kits/geostat-kit/docs/ADOPTION-LINE.md §8, docs/DEV-REMOTE.md
$OpsSecretsModule = "frontend"
$OpsComposeFile     = "docker-compose.yml"
# Remote dev overrides (optional): $OpsDevWatchPaths, $OpsDevSyncExcludes
# Optional overrides (defaults from ops/config/frontend/.env.deploy):
# $env:DEPLOY_LAYOUT = "structured"
# $env:DEPLOY_PATH_MODE = "base"
# $OpsToolkitRoot set in _init.ps1 from kits/geostat-kit
