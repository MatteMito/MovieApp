import { Module } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';
import { JwtModule } from '@nestjs/jwt';
import { HttpModule } from '@nestjs/axios';
import { MovieEntity } from './database/entities/movie.entity';
import { UserEntity } from './database/entities/user.entity';
import { UserMovieEntity } from './database/entities/user-movie.entity';
import { MovieListEntity } from './database/entities/list.entity';
import { MoviesModule } from './modules/movies/movies.module';
import { AuthModule } from './modules/auth/auth.module';
import { AnalyticsModule } from './modules/analytics/analytics.module';
import { ListsModule } from './modules/lists/lists.module';
import { WebsocketModule } from './modules/websocket/websocket.module';
import { DatabaseModule } from './database/database.module';
import { TmdbModule } from './modules/tmdb/tmdb.module';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      envFilePath: '.env',
    }),

    TypeOrmModule.forRootAsync({
      imports: [ConfigModule],
      useFactory: (configService: ConfigService) => ({
        type: 'postgres',
        host: configService.get('DB_HOST', 'localhost'),
        port: configService.get('DB_PORT', 5432),
        username: configService.get('DB_USERNAME', 'postgres'),
        password: configService.get('DB_PASSWORD', 'password'),
        database: configService.get('DB_NAME', 'movieapp'),
        
        entities: [
          MovieEntity,
          UserEntity,
          UserMovieEntity,
          MovieListEntity,
        ],
        
        synchronize: true,
        logging: true,
      }),
      inject: [ConfigService],
    }),

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

    HttpModule.register({
      timeout: 30000,
      maxRedirects: 5,
    }),

    DatabaseModule,
    MoviesModule,
    AuthModule,
    TmdbModule,
    AnalyticsModule,
    ListsModule,
    WebsocketModule,
  ],
})
export class AppModule {}