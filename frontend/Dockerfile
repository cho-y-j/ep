FROM node:20-alpine AS build
WORKDIR /app
COPY package.json ./
# 락파일은 host(linux-x64-gnu) 잔여 native 가 잡혀 alpine(musl) 빌드에서 깨짐 — 새로 install.
RUN npm install --no-audit --no-fund
COPY . .
# host 에서 끌고 온 잠긴 lock + install 흔적 제거 후 다시
RUN rm -f package-lock.json && rm -rf node_modules \
    && npm install --no-audit --no-fund
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
# nginx:alpine 은 기본 'nginx' user (101) 가 이미 존재. master 만 root, worker 는 nginx user.
# 포트 80 binding 위해 master 는 root 유지가 표준 (alpine nginx 기본).
EXPOSE 80
