FROM node:22-alpine AS build
WORKDIR /workspace

COPY web-admin/package*.json ./web-admin/
COPY frontend/packages/form-validation-kit/package*.json ./frontend/packages/form-validation-kit/
COPY frontend/packages/form-validation-kit ./frontend/packages/form-validation-kit

WORKDIR /workspace/web-admin
RUN npm ci

WORKDIR /workspace
COPY web-admin ./web-admin

RUN mkdir -p /workspace/frontend/packages/form-validation-kit && \
    ln -s /workspace/web-admin/node_modules /workspace/frontend/packages/form-validation-kit/node_modules

ARG VITE_API_BASE_URL=
ARG VITE_KEYCLOAK_URL=http://localhost:8182/auth
ARG VITE_KEYCLOAK_REALM=clinic-management
ARG VITE_KEYCLOAK_CLIENT_ID=clinic-web-admin

ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
ENV VITE_KEYCLOAK_URL=${VITE_KEYCLOAK_URL}
ENV VITE_KEYCLOAK_REALM=${VITE_KEYCLOAK_REALM}
ENV VITE_KEYCLOAK_CLIENT_ID=${VITE_KEYCLOAK_CLIENT_ID}

WORKDIR /workspace/web-admin
RUN npm run build

FROM nginx:1.27-alpine
COPY web-admin/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/web-admin/dist /usr/share/nginx/html

EXPOSE 80
HEALTHCHECK --interval=20s --timeout=5s --retries=5 --start-period=25s \
  CMD wget -q -O - http://127.0.0.1/ >/dev/null || exit 1
