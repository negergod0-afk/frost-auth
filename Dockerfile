FROM node:22-slim

WORKDIR /app

# Copy and install deps first (better layer caching)
COPY package.json ./
RUN npm install --production

# Copy server files only (bot runs separately)
COPY server/ ./server/
COPY start.js ./

# The mod payload jar — required at runtime for auth
COPY server/mod.jar ./server/mod.jar

EXPOSE 3000
EXPOSE 4000

CMD ["node", "start.js"]
