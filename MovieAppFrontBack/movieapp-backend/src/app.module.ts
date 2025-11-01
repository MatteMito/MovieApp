// file: src/app.module.ts
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
    ConfigModule.forRoot({
      isGlobal: true,
      envFilePath: '.env',
    }),
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
    ScheduleModule.forRoot(),
    DatabaseModule,
    MoviesModule,
    TmdbModule,
    AuthModule,
    ListsModule,
    WebsocketModule,
    AnalyticsModule,
  ],
  controllers: [AppController],
  providers: [],
})
export class AppModule {}