// modulo autenticazione con jwt tokens
// gestisce registrazione, login, validazione token

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
    // registra entita user per accesso database
    TypeOrmModule.forFeature([UserEntity]),
    
    // configura jwt module con secret e expiration da .env
    JwtModule.registerAsync({
      imports: [ConfigModule],
      useFactory: async (configService: ConfigService) => ({
        // secret key per firmare i token jwt utilizzato per verificare autenticita dei token
        secret: configService.get<string>('JWT_SECRET', 'movieapp-secret-key'),
        
        signOptions: {
          // durata validita token (es: 7d = 7 giorni)
          // dopo scadenza utente deve rifare login
          expiresIn: configService.get<string>('JWT_EXPIRATION', '7d'),
        },
      }),
      inject: [ConfigService],
    }),
  ],
  
  // controller per endpoint rest api (/register, /login)
  controllers: [AuthController],
  
  // service con logica business + strategy passport per validazione
  providers: [AuthService, JwtStrategy],
  
  // esporta authservice per essere usato in altri moduli
  exports: [AuthService],
})
export class AuthModule {}