FROM node:22-slim

WORKDIR /app

COPY package.json ./
RUN npm install --production

COPY server/ ./server/
COPY start.js ./

# Create data dir for persistent SQLite DB
RUN mkdir -p /app/data

ENV DB_PATH=/app/data/frost_auth.db

EXPOSE 3000
EXPOSE 4000

CMD ["node", "start.js"]
