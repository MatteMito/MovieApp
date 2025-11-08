// module autenticazione con jwt

import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { JwtModule } from '@nestjs/jwt';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { AuthController } from './auth.controller';
import { AuthService } from './auth.service';
import { UserEntity } from '../../database/entities/user.entity';
import { JwtStrategy } from './jwt.strategy';

@Module({
  imports: [
    // registra entity users per accesso database
    TypeOrmModule.forFeature([UserEntity]),
    
    // configura jwt module con valori da .env
    JwtModule.registerAsync({
      imports: [ConfigModule],
      useFactory: async (configService: ConfigService) => ({
        secret: configService.get<string>('JWT_SECRET', 'movieapp-secret-key'), // chiave segreta per firmare token
        signOptions: {
          expiresIn: configService.get<string>('JWT_EXPIRATION', '7d'), // durata validità token (default 7 giorni)
        },
      }),
      inject: [ConfigService],
    }),
  ],
  controllers: [AuthController],
  providers: [AuthService, JwtStrategy],
  exports: [AuthService],
})
export class AuthModule {}