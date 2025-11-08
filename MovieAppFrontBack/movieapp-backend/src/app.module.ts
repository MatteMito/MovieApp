// modulo principale dell'applicazione che configura tutti i moduli e dipendenze
// gestisce database postgresql, autenticazione jwt, websocket e api rest

import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';
import { ScheduleModule } from '@nestjs/schedule';
import { DatabaseModule } from './database/database.module';
import { MoviesModule } from './modules/movies/movies.module';
import { TmdbModule } from './modules/tmdb/tmdb.module';
import { AuthModule } from './modules/auth/auth.module';
import { ListsModule } from './modules/lists/lists.module';
import { WebsocketModule } from './modules/websocket/websocket.module';
import { AnalyticsModule } from './modules/analytics/analytics.module';
import { AppController } from './app.controller';

@Module({
  imports: [
    // configura caricamento variabili ambiente da file .env
    // isGlobal: true rende le variabili accessibili in tutti i moduli
    ConfigModule.forRoot({
      isGlobal: true,
      envFilePath: '.env',
    }),
    
    // configura connessione al database postgresql
    // usa typeorm per orm (object-relational mapping)
    // entities: auto-carica tutte le entita' dalla cartella
    // synchronize: false per usare migrations manuali (production-safe)
    // logging: false per non loggare tutte le query sql (performance)
    TypeOrmModule.forRoot({
      type: 'postgres',
      host: process.env.DB_HOST || 'localhost',
      port: parseInt(process.env.DB_PORT, 10) || 5432,
      username: process.env.DB_USER || 'postgres',
      password: process.env.DB_PASSWORD || 'postgres',
      database: process.env.DB_NAME || 'movieapp',
      entities: [__dirname + '/**/*.entity{.ts,.js}'],
      synchronize: false,
      logging: false,
    }),
    
    // abilita modulo scheduler per task periodici (cron jobs)
    ScheduleModule.forRoot(),
    
    // database module: gestione diretta del database postgresql
    DatabaseModule,
    
    // movies module: crud film, enrichment tmdb, batch operations
    MoviesModule,
    
    // tmdb module: integrazione api the movie database per dati film
    TmdbModule,
    
    // auth module: registrazione, login, jwt tokens
    AuthModule,
    
    // lists module: liste condivise tra utenti
    ListsModule,
    
    // websocket module: notifiche real-time per enrichment progress
    WebsocketModule,
    
    // analytics module: statistiche e grafici avanzati
    AnalyticsModule,
  ],
  
  controllers: [AppController],
  providers: [],
})
export class AppModule {}