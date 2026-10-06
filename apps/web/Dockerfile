FROM oven/bun:1.4.2 AS build
# Inlined into the browser bundle at build time; compose.prod.yaml passes the public URLs.
ARG VITE_API_URL=http://localhost:8080
ARG VITE_KEYCLOAK_URL=http://localhost:8081
ARG VITE_DEMO_MAIL_URL=
WORKDIR /app
COPY package.json bun.lock svelte.config.js tsconfig.json vite.config.ts .prettierrc.json ./
RUN bun install --frozen-lockfile
COPY src ./src
COPY static ./static
RUN bun run build

FROM oven/bun:1.4.2
WORKDIR /app
ENV NODE_ENV=production HOST=0.0.0.0 PORT=3000 ORIGIN=http://localhost:3000
COPY --from=build --chown=bun:bun /app/build ./build
COPY --from=build --chown=bun:bun /app/node_modules ./node_modules
COPY --from=build --chown=bun:bun /app/package.json ./package.json
USER bun
EXPOSE 3000
HEALTHCHECK --interval=15s --timeout=3s --start-period=20s --retries=5 \
    CMD bun -e 'fetch("http://127.0.0.1:3000/").then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))'
CMD ["bun", "./build/index.js"]
