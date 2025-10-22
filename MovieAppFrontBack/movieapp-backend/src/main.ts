import { NestFactory } from '@nestjs/core';
import { Logger } from '@nestjs/common';
import { AppModule } from './app.module';
import * as bodyParser from 'body-parser';

async function bootstrap() {
  const logger = new Logger('Bootstrap');

  const app = await NestFactory.create(AppModule, {
    logger: ['error', 'warn', 'log'],
  });

  // ⚠️ AUMENTA LIMITE BODY PARSER PER FILE CSV GRANDI
  // Default era 100kb, aumentiamo a 50MB per supportare import grandi
  app.use(bodyParser.json({ limit: '50mb' }));
  app.use(bodyParser.urlencoded({ limit: '50mb', extended: true }));

  // CORS per Android client
  app.enableCors({
    origin: '*',
    credentials: true,
    methods: 'GET,HEAD,PUT,PATCH,POST,DELETE,OPTIONS',
    allowedHeaders: 'Content-Type,Authorization',
  });

  const port = process.env.PORT || 3001;
  const host = process.env.HOST || '0.0.0.0';

  await app.listen(port, host);

  logger.log(`🚀 MovieApp Backend v2.1 attivo su http://${host}:${port}`);
  logger.log(`📚 Database: PostgreSQL`);
  logger.log(`🔄 WebSocket: attivo`);
  logger.log(`📦 Body Parser Limit: 50MB`);
  logger.log(`✅ CORS: abilitato per tutti gli origin`);
  logger.log(`\n=== Backend pronto per ricevere richieste ===\n`);
}

bootstrap();