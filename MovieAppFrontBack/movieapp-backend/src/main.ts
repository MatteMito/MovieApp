// bootstrap applicazione nestjs

import { NestFactory } from '@nestjs/core';
import { Logger, ValidationPipe } from '@nestjs/common';
import { AppModule } from './app.module';
import { ConfigService } from '@nestjs/config';
import * as os from 'os';

/**
 * Ottiene l'IP principale della rete locale
 */
function getLocalNetworkIP(): string | null {
  const interfaces = os.networkInterfaces();
  const priorityOrder = ['Wi-Fi', 'WiFi', 'en0', 'eth0', 'Ethernet'];
  
  for (const priority of priorityOrder) {
    const iface = interfaces[priority];
    if (iface) {
      for (const details of iface) {
        if (details.family === 'IPv4' && !details.internal) {
          return details.address;
        }
      }
    }
  }
  
  for (const name of Object.keys(interfaces)) {
    if (name.includes('vEthernet') || name.includes('VirtualBox') || name.includes('VMware')) {
      continue;
    }
    const iface = interfaces[name];
    if (iface) {
      for (const details of iface) {
        if (details.family === 'IPv4' && !details.internal) {
          return details.address;
        }
      }
    }
  }
  
  return null;
}

async function bootstrap() {
  const logger = new Logger('Bootstrap');

  // crea applicazione nestjs con logging ridotto
  const app = await NestFactory.create(AppModule, {
    logger: ['error', 'warn', 'log'],
  });

  const configService = app.get(ConfigService);

  // validazione automatica dto
  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
    }),
  );

  // cors per client android
  app.enableCors({
    origin: '*',
    methods: 'GET,HEAD,PUT,PATCH,POST,DELETE,OPTIONS',
    credentials: true,
    allowedHeaders: 'Content-Type,Authorization',
  });

  // prefix globale api
  app.setGlobalPrefix('');

  // porta e host da environment
  const port = configService.get('PORT', 3001);
  const host = configService.get('HOST', '0.0.0.0');

  await app.listen(port, host);

  // Rileva IP rete locale
  const localIP = getLocalNetworkIP();

  //tutti i log stampati
  logger.log('='.repeat(70));
  logger.log('🚀 MOVIEAPP BACKEND v2.0');
  logger.log('='.repeat(70));
  logger.log(`📊 Database: PostgreSQL | 🔐 Auth: JWT | ⚡ Cache: Attiva`);
  logger.log('');
  logger.log('📍 URL DISPONIBILI:');
  logger.log(`   Localhost:     http://localhost:${port}`);
  
  if (localIP) {
    logger.log(`   Network IP:    http://${localIP}:${port}`);
  } else {
    logger.warn('   Network IP:    Non rilevato');
  }
  
  logger.log('');
  logger.log('⚡ WEBSOCKET:');
  logger.log(`   Localhost:     ws://localhost:${port}/ws`);
  
  if (localIP) {
    logger.log(`   Network IP:    ws://${localIP}:${port}/ws`);
  }
  
  logger.log('');
  logger.log('📋 ENDPOINT:');
  logger.log(`   GET  /api/v1/movies/health`);
  logger.log(`   GET  /api/v1/movies/all`);
  logger.log(`   POST /api/v1/movies/enrich`);
  logger.log(`   POST /api/v1/movies/batch`);
  logger.log('');
  
  if (localIP) {
    logger.log(`💡 Usa questo IP per Android: ${localIP}:${port}`);
  } else {
    logger.warn('⚠️  IP rete locale non rilevato. Verifica WiFi.');
  }
  
  logger.log('='.repeat(70));
  logger.log('✅ SISTEMA PRONTO!');
  logger.log('='.repeat(70));
}

bootstrap();