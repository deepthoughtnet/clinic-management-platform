FROM node:22-alpine AS build
WORKDIR /workspace

COPY web-public/package*.json ./web-public/
COPY frontend/packages/form-validation-kit/package*.json ./frontend/packages/form-validation-kit/
COPY frontend/packages/form-validation-kit ./frontend/packages/form-validation-kit

WORKDIR /workspace/web-public
RUN npm ci

WORKDIR /workspace
COPY web-public ./web-public

RUN mkdir -p /workspace/frontend/packages/form-validation-kit && \
    ln -s /workspace/web-public/node_modules /workspace/frontend/packages/form-validation-kit/node_modules

ARG VITE_PUBLIC_API_BASE_URL=
ENV VITE_PUBLIC_API_BASE_URL=${VITE_PUBLIC_API_BASE_URL}
ARG VITE_API_BASE_URL=
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
ARG VITE_CLINIC_LOGIN_URL=
ENV VITE_CLINIC_LOGIN_URL=${VITE_CLINIC_LOGIN_URL}
ARG VITE_AIVA_APP_URL=
ENV VITE_AIVA_APP_URL=${VITE_AIVA_APP_URL}
ARG VITE_CAREAI_RUNTIME_URL=
ENV VITE_CAREAI_RUNTIME_URL=${VITE_CAREAI_RUNTIME_URL}

WORKDIR /workspace/web-public
RUN npm run build

FROM nginx:1.27-alpine
COPY web-public/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/web-public/dist /usr/share/nginx/html

EXPOSE 80
HEALTHCHECK --interval=20s --timeout=5s --retries=5 --start-period=25s \
  CMD wget -q -O - http://127.0.0.1/ >/dev/null || exit 1
