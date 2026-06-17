FROM node:22-alpine AS build
WORKDIR /app

COPY package*.json /app/
RUN npm ci

COPY . /app

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

RUN npm run build

FROM nginx:1.27-alpine
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
HEALTHCHECK --interval=20s --timeout=5s --retries=5 --start-period=25s \
  CMD wget -q -O - http://127.0.0.1/ >/dev/null || exit 1
