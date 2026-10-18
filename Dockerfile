FROM node:22-alpine
WORKDIR /app
COPY server/server.js .
CMD ["node", "server.js"]
