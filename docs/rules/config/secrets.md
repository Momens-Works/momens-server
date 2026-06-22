# 시크릿

- 실제 secret은 커밋하지 않습니다. 커밋 가능한 설정 파일에는 환경변수 placeholder만 둡니다.
- 커밋 금지: `.env`, `.env.*`, `application-secret.yml`, `application-*-secret.yml`.
  커밋 가능: `application.yml`, `application-{local,test,prod}.yml`, `.env.example`.
- secret 주입: 로컬은 `.env`, CI는 GitHub Actions Secrets, 운영은 Kubernetes Secret 또는
  External Secrets.
- 개인 DM·개인 메모·private submodule·private repo를 secret 저장소로 쓰지 않습니다.
- 자세한 운영은 [로컬 개발](../../LOCAL_DEVELOPMENT.md)을 참고합니다.
