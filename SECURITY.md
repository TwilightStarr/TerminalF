# Security policy

## Reporting a vulnerability

If you find a security issue, please **do not** open a public issue. Use GitHub's **Security > Report a vulnerability**
(private security advisory) feature on this repository.

## Design principles

- No data is sent over the network; there is no telemetry.
- Package names passed to the shell are validated (command-injection guard).
- System-critical packages and the app itself cannot be frozen or removed.
- Privileges (root/Shizuku) are granted explicitly by the user; readiness is verified with a real command.

---

## Güvenlik politikası (Türkçe)

Bir açık bulduysanız herkese açık issue açmayın; GitHub'da **Security > Report a vulnerability** özelliğini kullanın.
Ağa veri gönderilmez, telemetri yoktur; kabuğa verilen paket adları doğrulanır; kritik paketler korunur; yetki
kullanıcı tarafından açıkça verilir.
