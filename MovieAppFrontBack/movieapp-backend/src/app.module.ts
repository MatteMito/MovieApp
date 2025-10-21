//modulo principale applicazione nestjs

import { Module } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';
import { JwtModule } from '@nestjs/jwt';
import { HttpModule } from '@nestjs/axios';
import { MovieEntity } from './database/entities/movie.entity';
import { UserEntity } from './database/entities/user.entity';
import { TmdbCacheEntity } from './database/entities/tmdb-cache.entity';
import { MoviesModule } from './modules/movies/movies.module';
import { AuthModule } from './modules/auth/auth.module';
import { AnalyticsModule } from './modules/analytics/analytics.module';
import { ListsModule } from './modules/lists/lists.module';
import { WebsocketModule } from './modules/websocket/websocket.module';

@Module({
  imports: [
    //configurazione ambiente
    ConfigModule.forRoot({
      isGlobal: true,
      envFilePath: '.env',
    }),

    //database postgresql con typeorm
    TypeOrmModule.forRootAsync({
      imports: [ConfigModule],
      useFactory: (configService: ConfigService) => ({
        type: 'postgres',
        host: configService.get('DB_HOST', 'localhost'),
        port: configService.get('DB_PORT', 5432),
        username: configService.get('DB_USERNAME', 'postgres'),
        password: configService.get('DB_PASSWORD', 'password'),
        database: configService.get('DB_NAME', 'movieapp'),
        entities: [MovieEntity, UserEntity, TmdbCacheEntity],
        synchronize: configService.get('NODE_ENV') !== 'production',
        logging: true,
      }),
      inject: [ConfigService],
    }),

    //jwt per autenticazione
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

    //http module per chiamate api esterne
    HttpModule.register({
      timeout: 30000,
      maxRedirects: 5,
    }),

    //feature modules
    MoviesModule,
    AuthModule,
    AnalyticsModule,
    ListsModule,
    WebsocketModule,
  ],
})
export class AppModule {}