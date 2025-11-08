// module principale applicazione con configurazione database e moduli

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
    // configura variabili ambiente da .env
    ConfigModule.forRoot({
      isGlobal: true, // rende config disponibile in tutti i moduli
      envFilePath: '.env',
    }),
    
    // configura connessione postgresql con typeorm
    TypeOrmModule.forRoot({
      type: 'postgres',
      host: process.env.DB_HOST || 'localhost',
      port: parseInt(process.env.DB_PORT, 10) || 5432,
      username: process.env.DB_USER || 'postgres',
      password: process.env.DB_PASSWORD || 'postgres',
      database: process.env.DB_NAME || 'movieapp',
      entities: [__dirname + '/**/*.entity{.ts,.js}'], // carica tutte le entity
      synchronize: false, // disattivato per usare migration manuali
      logging: false, // disattiva log query sql (abilita per debug)
    }),
    
    // abilita cron jobs schedulati (sync tmdb, cleanup)
    ScheduleModule.forRoot(),
    
    // moduli applicazione
    DatabaseModule, // service database custom
    MoviesModule, // gestione film e enrichment
    TmdbModule, // integrazione tmdb api
    AuthModule, // autenticazione jwt
    ListsModule, // liste personalizzate
    WebsocketModule, // notifiche real-time
    AnalyticsModule, // statistiche utente
  ],
  controllers: [AppController], // controller root con info api
  providers: [],
})
export class AppModule {}