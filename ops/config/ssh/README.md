# SSH — სად ჩასვა გასაღები (clone-ის შემდეგ)

Deploy (`geostat fe/be deploy`, `manage`, `dev`) იყენებს **OpenSSH**-ს (`ssh` / `scp`).  
პრივატული გასაღები **არასოდეს** არ უნდა იყოს git-ში.

## სწრაფი არჩევანი

| მეთოდი | სად არის key | რა ჩაწერო `ops/config/deploy.env`-ში |
|--------|--------------|-------------------------------------|
| **A — სტანდარტული (რეკომენდებული)** | `~/.ssh/id_ed25519` (ან `id_rsa`) | `DEPLOY_SERVER=user@host` |
| **B — პროექტის ფოლდერი** | `ops/config/ssh/id_ed25519` (gitignored) | `DEPLOY_SERVER=user@host` + `DEPLOY_SSH_IDENTITY_FILE=ops/config/ssh/id_ed25519` |
| **C — SSH config alias** | `ops/config/ssh/config` (gitignored) | `DEPLOY_SERVER=geostat-deploy` + `DEPLOY_SSH_CONFIG_FILE=ops/config/ssh/config` |

## A — სისტემური `~/.ssh` (უმეტესი გუნდი)

1. გენერაცია (ერთხელ):
   ```bash
   ssh-keygen -t ed25519 -C "your@email" -f ~/.ssh/id_ed25519
   ```
2. სერვერზე public key:
   ```bash
   ssh-copy-id user@YOUR_SERVER
   ```
3. `ops/config/deploy.env`:
   ```env
   DEPLOY_SERVER=administrator@192.168.1.199
   ```
4. შემოწმება:
   ```powershell
   .\tools\geostat.ps1 be check
   ```

## B — პროექტში `ops/config/ssh/` (გადმოწერისას ნათელი ადგილი)

1. დააკოპირე შენი **პრივატული** გასაღები (არა `.pub`):
   ```text
   ops/config/ssh/id_ed25519      ← პრივატული key (chmod 600 Linux-ზე)
   ops/config/ssh/id_ed25519.pub  ← optional, მხოლოდ დოკუმენტაციისთვის
   ```
2. `ops/config/deploy.env`:
   ```env
   DEPLOY_SERVER=administrator@192.168.1.199
   DEPLOY_SSH_IDENTITY_FILE=ops/config/ssh/id_ed25519
   ```
3. Windows-ზე path შეიძლება იყოს აბსოლუტური:
   ```env
   DEPLOY_SSH_IDENTITY_FILE=C:/Users/you/.ssh/id_ed25519
   ```

## C — `ops/config/ssh/config` (Host alias)

1. `copy secrets\ssh\config.example secrets\ssh\config`
2. შეავსე `HostName`, `User`, `IdentityFile`
3. `ops/config/deploy.env`:
   ```env
   DEPLOY_SERVER=geostat-deploy
   DEPLOY_SSH_CONFIG_FILE=ops/config/ssh/config
   ```
4. პირველი კავშირი:
   ```bash
   ssh -F ops/config/ssh/config geostat-deploy
   ```

## ფაილების ხე

```text
ops/config/ssh/
├── README.md              ← ეს (commit-ში)
├── config.example         ← ნიმუში → დააკოპირე config-ად
├── id_ed25519.example     ← არა რეალური key — ინსტრუქცია
├── config                 ← gitignored (შენი)
├── id_ed25519             ← gitignored (შენი პრივატული key)
└── known_hosts            ← optional, gitignored
```

## რა **არ** უნდა

- private key commit `git push`-ში
- გასაღები `kits/geostat-kit/`-ში
- password-ის ჩაწერა `deploy.env`-ში (გამოიყენე key-based auth)

## დაკავშირება ops-თან

| ცვლადი | ფაილი |
|--------|--------|
| `DEPLOY_SERVER` | `ops/config/deploy.env` — **აუცილებელი** remote-ისთვის |
| `DEPLOY_SSH_IDENTITY_FILE` | optional — პროექტის key path |
| `DEPLOY_SSH_CONFIG_FILE` | optional — `ssh -F` config |
| `DEPLOY_SSH_OPTIONS` | optional — მაგ. `-o StrictHostKeyChecking=accept-new` |

სრული რუკა: [docs/CONFIG.md](../../docs/CONFIG.md)
