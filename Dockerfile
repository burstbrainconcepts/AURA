FROM node:22-bookworm-slim

ENV NODE_ENV=production
WORKDIR /app

COPY package.json ./
COPY packages ./packages
COPY recovery-console ./recovery-console

RUN npm install --omit=dev --ignore-scripts     && npm cache clean --force

USER node
EXPOSE 8787

CMD ["sh", "-c", "npm run migrate --workspace @aura/recovery-server && npm run start --workspace @aura/recovery-server"]
