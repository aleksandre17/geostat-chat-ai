# Module ops config — see kits/geostat-kit/docs/ADOPTION-LINE.md §9
OPS_SECRETS_MODULE="backend"
# Optional fallback if DEPLOY_PROJECT unset in ops/config/deploy.env (else: repo folder name)
OPS_PROJECT_NAME=""
OPS_TARGET_DEFAULT="backend"
VERSIONS_KEEP=5
HEALTH_RETRIES=24
CRED_PATTERNS="*.p12 *.jks *.keystore *.pem *.crt *credentials*.json *service-account*.json"
