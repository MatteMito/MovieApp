// punto di ingresso principale dell'applicazione nestjs
// configura il server, cors, body parser e avvia il backend

import { NestFactory } from '@nestjs/core';
import { Logger } from '@nestjs/common';
import { AppModule } from './app.module';
import * as bodyParser from 'body-parser';

async function bootstrap() {
  // logger per monitorare l'avvio del server
  const logger = new Logger('Bootstrap');

  // crea l'applicazione nestjs con logging configurato
  // log levels: error (errori critici), warn (avvisi), log (info generali)
  const app = await NestFactory.create(AppModule, {
    logger: ['error', 'warn', 'log'],
  });

  // configura body parser per gestire richieste con payload grandi
  // limite aumentato a 50mb per supportare import di csv voluminosi
  // default nestjs e' 100kb, insufficiente per file letterboxd grandi
  app.use(bodyParser.json({ limit: '50mb' }));
  app.use(bodyParser.urlencoded({ limit: '50mb', extended: true }));

  // abilita cors per permettere richieste dal client android
  // origin: '*' = accetta richieste da qualsiasi origine (sviluppo)
  // credentials: true = permette invio di cookies e header di autenticazione
  // methods: tutti i metodi http necessari per api rest
  // allowedHeaders: headers personalizzati permessi nelle richieste
  app.enableCors({
    origin: '*',
    credentials: true,
    methods: 'GET,HEAD,PUT,PATCH,POST,DELETE,OPTIONS',
    allowedHeaders: 'Content-Type,Authorization',
  });

  // legge porta e host da variabili ambiente o usa default
  // port 3001 per non conflittare con altre applicazioni
  // host 0.0.0.0 per accettare connessioni da qualsiasi interfaccia di rete
  const port = process.env.PORT || 3001;
  const host = process.env.HOST || '0.0.0.0';

  // avvia il server in ascolto su host e porta specificati
  await app.listen(port, host);

  // log informativi sull'avvio del server
  logger.log(`movieapp backend v1.0 attivo su http://${host}:${port}`);
  logger.log(`database: postgresql`);
  logger.log(`websocket: attivo`);
  logger.log(`body parser limit: 50mb`);
  logger.log(`cors: abilitato per tutti gli origin`);
  logger.log(`\n=== backend pronto per ricevere richieste ===\n`);
}

// avvia la funzione bootstrap
bootstrap();