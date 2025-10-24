// Modulo principale applicazione NestJS

import { Module } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';
import { JwtModule } from '@nestjs/jwt';
import { HttpModule } from '@nestjs/axios';

// ✅ Import di TUTTE le entities
import { MovieEntity } from './database/entities/movie.entity';
import { UserEntity } from './database/entities/user.entity';
import { TmdbCacheEntity } from './database/entities/tmdb-cache.entity';
import { UserMovieEntity } from './database/entities/user-movie.entity';

// Modules
import { MoviesModule } from './modules/movies/movies.module';
import { AuthModule } from './modules/auth/auth.module';
import { AnalyticsModule } from './modules/analytics/analytics.module';
import { ListsModule } from './modules/lists/lists.module';
import { WebsocketModule } from './modules/websocket/websocket.module';

@Module({
  imports: [
    // Configurazione ambiente
    ConfigModule.forRoot({
      isGlobal: true,
      envFilePath: '.env',
    }),

    // Database PostgreSQL con TypeORM
    TypeOrmModule.forRootAsync({
      imports: [ConfigModule],
      useFactory: (configService: ConfigService) => ({
        type: 'postgres',
        host: configService.get('DB_HOST', 'localhost'),
        port: configService.get('DB_PORT', 5432),
        username: configService.get('DB_USERNAME', 'postgres'),
        password: configService.get('DB_PASSWORD', 'password'),
        database: configService.get('DB_NAME', 'movieapp'),
        
        // ✅ Tutte le entities incluse
        entities: [
          MovieEntity,
          UserEntity,
          TmdbCacheEntity,
          UserMovieEntity, // ⬅️ AGGIUNTA
        ],
        
        // ⚠️ DISABILITA synchronize dopo aver eseguito schema.sql
        // Usa migrations manuali per evitare problemi
        synchronize: false,
        
        logging: true,
      }),
      inject: [ConfigService],
    }),

    // JWT per autenticazione
    JwtModule.registerAsync({
      imports: [ConfigModule],
      useFactory: (configService: ConfigService) => ({
        secret: configService.get('JWT_SECRET', 'movieapp-secret-key'),
        signOptions: {
          expiresIn: configService.get('JWT_EXPIRATION', '7d'),
        },
      }),
      inject: [ConfigService],
      global: true,
    }),

    // HTTP module per chiamate API esterne
    HttpModule.register({
      timeout: 30000,
      maxRedirects: 5,
    }),

    // Feature modules
    MoviesModule,
    AuthModule,
    AnalyticsModule,
    ListsModule,
    WebsocketModule,
  ],
})
export class AppModule {}