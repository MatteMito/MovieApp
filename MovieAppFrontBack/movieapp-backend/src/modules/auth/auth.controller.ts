//controller autenticazione rest api

import {
  Controller,
  Post,
  Body,
  HttpException,
  HttpStatus,
  Logger,
} from '@nestjs/common';
import { AuthService, RegisterDto, LoginDto } from './auth.service';

@Controller('api/v1/auth')
export class AuthController {
  private readonly logger = new Logger(AuthController.name);

  constructor(private readonly authService: AuthService) {}

  /**
   * POST /api/v1/auth/register
   * registrazione nuovo utente
   */
  @Post('register')
  async register(@Body() dto: RegisterDto) {
    try {
      this.logger.log(`📝 richiesta registrazione: ${dto.email}`);

      //validazione input
      if (!dto.email || !dto.password) {
        throw new HttpException(
          {
            success: false,
            message: 'email e password obbligatori',
            timestamp: new Date().toISOString(),
          },
          HttpStatus.BAD_REQUEST,
        );
      }

      if (dto.password.length < 6) {
        throw new HttpException(
          {
            success: false,
            message: 'password troppo corta (minimo 6 caratteri)',
            timestamp: new Date().toISOString(),
          },
          HttpStatus.BAD_REQUEST,
        );
      }

      const result = await this.authService.register(dto);

      this.logger.log(`✅ registrazione completata: ${dto.email}`);

      return {
        success: true,
        data: result,
        message: 'registrazione completata',
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`❌ errore registrazione: ${error.message}`);

      if (error instanceof HttpException) {
        throw error;
      }

      throw new HttpException(
        {
          success: false,
          message: error.message || 'errore registrazione',
          timestamp: new Date().toISOString(),
        },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * POST /api/v1/auth/login
   * login utente esistente
   */
  @Post('login')
  async login(@Body() dto: LoginDto) {
    try {
      this.logger.log(`🔐 richiesta login: ${dto.email}`);

      //validazione input
      if (!dto.email || !dto.password) {
        throw new HttpException(
          {
            success: false,
            message: 'email e password obbligatori',
            timestamp: new Date().toISOString(),
          },
          HttpStatus.BAD_REQUEST,
        );
      }

      const result = await this.authService.login(dto);

      this.logger.log(`✅ login completato: ${dto.email}`);

      return {
        success: true,
        data: result,
        message: 'login completato',
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`❌ errore login: ${error.message}`);

      if (error instanceof HttpException) {
        throw error;
      }

      throw new HttpException(
        {
          success: false,
          message: error.message || 'credenziali non valide',
          timestamp: new Date().toISOString(),
        },
        HttpStatus.UNAUTHORIZED,
      );
    }
  }
}