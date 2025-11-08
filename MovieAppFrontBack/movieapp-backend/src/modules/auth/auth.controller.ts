// controller autenticazione rest api
// endpoint per registrazione e login utenti

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
   * endpoint: POST /api/v1/auth/register
   * registrazione nuovo utente
   * body: { email, password, username? }
   * response: { success, data: { access_token, user }, message, timestamp }
   */
  @Post('register')
  async register(@Body() dto: RegisterDto) {
    try {
      this.logger.log(`richiesta registrazione: ${dto.email}`);

      // validazione input lato server
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

      // verifica lunghezza minima password
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

      // chiama service per registrazione
      const result = await this.authService.register(dto);

      this.logger.log(`registrazione completata: ${dto.email}`);

      // response success standardizzata
      return {
        success: true,
        data: result,
        message: 'registrazione completata',
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore registrazione: ${error.message}`);

      // rilancia httpexception se gia presente
      if (error instanceof HttpException) {
        throw error;
      }

      // altrimenti crea nuova httpexception
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
   * endpoint: POST /api/v1/auth/login
   * login utente esistente
   * body: { email, password }
   * response: { success, data: { access_token, user }, message, timestamp }
   */
  @Post('login')
  async login(@Body() dto: LoginDto) {
    try {
      this.logger.log(`richiesta login: ${dto.email}`);

      // validazione input
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

      // chiama service per login
      const result = await this.authService.login(dto);

      this.logger.log(`login completato: ${dto.email}`);

      // response success standardizzata
      return {
        success: true,
        data: result,
        message: 'login completato',
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore login: ${error.message}`);

      // rilancia httpexception se gia presente
      if (error instanceof HttpException) {
        throw error;
      }

      // altrimenti crea nuova httpexception
      throw new HttpException(
        {
          success: false,
          message: error.message || 'errore login',
          timestamp: new Date().toISOString(),
        },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }
}