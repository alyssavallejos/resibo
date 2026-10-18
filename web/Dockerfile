# Step 1: Build the Vite Single Page Application
FROM node:20-alpine AS builder
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

# Step 2: Serve with lightweight Nginx container
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Cloud Run defaults to port 8080
EXPOSE 8080

CMD ["nginx", "-g", "daemon off;"]
