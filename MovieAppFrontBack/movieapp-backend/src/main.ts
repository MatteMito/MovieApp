// entry point applicazione nestjs

import { NestFactory } from '@nestjs/core';
import { Logger } from '@nestjs/common';
import { AppModule } from './app.module';
import * as bodyParser from 'body-parser';

async function bootstrap() {
  const logger = new Logger('Bootstrap');

  // crea applicazione nestjs con logging configurato
  const app = await NestFactory.create(AppModule, {
    logger: ['error', 'warn', 'log'], // log solo errori, warning e info
  });

  // aumenta limite body parser per file csv grandi
  // default era 100kb, aumentato a 50mb per supportare batch import grandi
  app.use(bodyParser.json({ limit: '50mb' }));
  app.use(bodyParser.urlencoded({ limit: '50mb', extended: true }));

  // abilita cors per android client e frontend
  app.enableCors({
    origin: '*', // permetti tutte le origini (dev mode)
    credentials: true, // permetti cookie e auth headers
    methods: 'GET,HEAD,PUT,PATCH,POST,DELETE,OPTIONS',
    allowedHeaders: 'Content-Type,Authorization',
  });

  // leggi porta e host da .env o usa default
  const port = process.env.PORT || 3001;
  const host = process.env.HOST || '0.0.0.0'; // ascolta su tutte le interfacce

  await app.listen(port, host);

  // log startup info
  logger.log(`🚀 MovieApp Backend v1.0 attivo su http://${host}:${port}`);
  logger.log(`📚 Database: PostgreSQL`);
  logger.log(`🔄 WebSocket: attivo`);
  logger.log(`📦 Body Parser Limit: 50MB`);
  logger.log(`✅ CORS: abilitato per tutti gli origin`);
  logger.log(`\n=== Backend pronto per ricevere richieste ===\n`);
}

bootstrap();