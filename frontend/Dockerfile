FROM node:22-alpine AS build
WORKDIR /workspace

ARG VITE_APP_BUILD_VERSION=server
ENV VITE_APP_BUILD_VERSION=$VITE_APP_BUILD_VERSION

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 80 443
