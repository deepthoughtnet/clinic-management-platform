FROM node:22-alpine AS build
WORKDIR /workspace

COPY web-discover/package*.json ./web-discover/

WORKDIR /workspace/web-discover
RUN npm ci

WORKDIR /workspace
COPY web-discover ./web-discover

ARG VITE_API_BASE_URL=
ARG VITE_CARE_APP_URL=
ARG VITE_HEALTHCARE_APP_URL=
ARG VITE_AIVA_APP_URL=
ARG VITE_ENV_NAME=
ARG VITE_ANALYTICS_ID=
ARG VITE_ANALYTICS_ENABLED=false

ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
ENV VITE_CARE_APP_URL=${VITE_CARE_APP_URL}
ENV VITE_HEALTHCARE_APP_URL=${VITE_HEALTHCARE_APP_URL}
ENV VITE_AIVA_APP_URL=${VITE_AIVA_APP_URL}
ENV VITE_ENV_NAME=${VITE_ENV_NAME}
ENV VITE_ANALYTICS_ID=${VITE_ANALYTICS_ID}
ENV VITE_ANALYTICS_ENABLED=${VITE_ANALYTICS_ENABLED}

WORKDIR /workspace/web-discover
RUN npm run build

FROM nginx:1.27-alpine
COPY web-discover/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/web-discover/dist /usr/share/nginx/html

EXPOSE 80
HEALTHCHECK --interval=20s --timeout=5s --retries=5 --start-period=25s \
  CMD wget -q -O - http://127.0.0.1/ >/dev/null || exit 1
