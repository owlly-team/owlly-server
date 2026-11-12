# 배포 / CI-CD

## 구성 요약
- **Dockerfile**: Java 21 멀티스테이지 빌드(`gradle bootJar` → JRE 실행, 비루트).
- **docker-compose.yml**: `postgres` + `server` 를 함께 기동. server 는 postgres 가 healthy 가 된 뒤 시작.
- **.github/workflows/deploy.yml**: `main` 푸시 시 → 테스트 → GHCR 이미지 빌드·푸시 → 운영 서버에 SSH 로 pull & 재기동.

## 로컬 실행
```bash
cp .env.example .env   # 값 채우기 (DB_HOST 는 compose 가 postgres 로 덮어씀)
docker compose up -d --build
# http://localhost:8080/actuator/health → {"status":"UP"}
```

## 운영 서버 최초 1회 준비 (EC2)
배포 대상은 **AWS EC2** 인스턴스다.

1. **보안 그룹(Security Group)** 인바운드 규칙
   - `22` (SSH) — GitHub Actions 러너가 접속. (고정 IP 가 없으므로 운영 시엔 SSM/배스천 사용 권장, 데모는 열어둠)
   - `8080` (앱) — 또는 앞단에 Nginx/ALB 를 둘 경우 `80`/`443` 만 열고 8080 은 내부로.
   - `5432` 는 **외부로 열지 말 것** (compose 내부 네트워크로만 접근).
2. **Docker 설치** (Amazon Linux 2023 기준)
   ```bash
   sudo dnf install -y docker
   sudo systemctl enable --now docker
   sudo usermod -aG docker $USER   # 재로그인 후 sudo 없이 docker 사용
   # Compose 플러그인
   sudo dnf install -y docker-compose-plugin || {
     sudo mkdir -p /usr/local/lib/docker/cli-plugins
     sudo curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
       -o /usr/local/lib/docker/cli-plugins/docker-compose
     sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
   }
   ```
   > Ubuntu AMI 를 쓰면 `apt-get install docker.io docker-compose-plugin`.
3. **배포 경로** 디렉터리만 생성 (`mkdir -p /home/ubuntu/owlly`).
   - `docker-compose.yml` 과 `.env` 는 CI 가 매 배포마다 `ENV_FILE` Secret 으로부터 자동으로 써 넣으므로 수동 배치 불필요.
4. 비공개 패키지면 최초 1회 `docker login ghcr.io` (이후는 CI 가 처리).

## 필요한 GitHub Secrets
| Secret | 설명 |
|--------|------|
| `SSH_HOST` | EC2 퍼블릭 IP 또는 도메인 (탄력적 IP 권장) |
| `SSH_USER` | EC2 접속 계정 — Amazon Linux: `ec2-user`, Ubuntu AMI: `ubuntu` |
| `SSH_KEY` | EC2 키페어 `.pem` 개인키 **전체 내용** |
| `SSH_PORT` | SSH 포트 (기본 `22`) |
| `DEPLOY_PATH` | 서버의 배포 디렉터리 (예: `/home/ubuntu/owlly`) |
| `ENV_FILE` | `.env` 파일 **전체 내용**. 배포 시 CI 가 서버의 `DEPLOY_PATH/.env` 로 써 넣는다. `.env` 값이 바뀌면 이 Secret 을 갱신 후 다시 배포. |

> GHCR 푸시/로그인은 기본 제공되는 `GITHUB_TOKEN` 을 사용하므로 별도 토큰 불필요.
> 이미지: `ghcr.io/owlly-team/owlly-server:latest`

## 동작 순서 (main 푸시 시)
1. `test` — `./gradlew test`
2. `build-and-push` — 이미지 빌드 후 GHCR 에 `latest` + `sha-<commit>` 태그로 푸시
3. `deploy` — compose 파일을 서버로 복사 → `docker compose pull && up -d`
