// service autenticazione con jwt e bcrypt
// gestisce registrazione utenti, login, validazione token, hashing password

import { Injectable, Logger, UnauthorizedException } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import * as bcrypt from 'bcrypt';
import { UserEntity } from '../../database/entities/user.entity';

// dto per registrazione nuovo utente
export interface RegisterDto {
  email: string;
  password: string;
  username?: string; // opzionale
}

// dto per login utente esistente
export interface LoginDto {
  email: string;
  password: string;
}

// response standardizzata autenticazione
export interface AuthResponse {
  access_token: string;
  user: {
    id: string;
    email: string;
    username?: string;
  };
}

@Injectable()
export class AuthService {
  private readonly logger = new Logger(AuthService.name);

  constructor(
    // repository per accesso tabella users
    @InjectRepository(UserEntity)
    private userRepository: Repository<UserEntity>,
    
    // service jwt per generare e validare token
    private jwtService: JwtService,
  ) {}

  /**
   * registrazione nuovo utente
   * 1. verifica email non gia esistente
   * 2. hash password con bcrypt (sicurezza)
   * 3. salva utente nel database
   * 4. genera jwt token per login automatico
   */
  async register(dto: RegisterDto): Promise<AuthResponse> {
    try {
      this.logger.log(`tentativo registrazione: ${dto.email}`);

      // verifica se email gia esistente nel database
      const existingUser = await this.userRepository.findOne({
        where: { email: dto.email },
      });

      if (existingUser) {
        throw new UnauthorizedException('email gia registrata');
      }

      // hash password con bcrypt (10 rounds = buon bilanciamento sicurezza/performance)
      // mai salvare password in chiaro nel database per sicurezza
      const hashedPassword = await bcrypt.hash(dto.password, 10);

      // crea nuovo utente
      const user = this.userRepository.create({
        email: dto.email,
        password: hashedPassword,
        username: dto.username,
        is_active: true,
      });

      // salva nel database
      const savedUser = await this.userRepository.save(user);

      this.logger.log(`utente registrato: ${savedUser.email}`);

      // genera jwt token per login automatico dopo registrazione
      const token = this.generateToken(savedUser);

      return {
        access_token: token,
        user: {
          id: savedUser.id,
          email: savedUser.email,
          username: savedUser.username,
        },
      };
    } catch (error) {
      this.logger.error(`errore registrazione: ${error.message}`);
      throw error;
    }
  }

  /**
   * login utente esistente
   * 1. cerca utente per email
   * 2. verifica password con bcrypt.compare
   * 3. aggiorna timestamp last_login
   * 4. genera jwt token
   */
  async login(dto: LoginDto): Promise<AuthResponse> {
    try {
      this.logger.log(`tentativo login: ${dto.email}`);

      // cerca utente per email
      const user = await this.userRepository.findOne({
        where: { email: dto.email },
      });

      if (!user) {
        // non rivelare se email esiste o no (sicurezza)
        throw new UnauthorizedException('credenziali non valide');
      }

      // verifica password confrontando con hash salvato nel database
      // bcrypt.compare gestisce automaticamente il confronto sicuro
      const isPasswordValid = await bcrypt.compare(dto.password, user.password);

      if (!isPasswordValid) {
        throw new UnauthorizedException('credenziali non valide');
      }

      // aggiorna timestamp ultimo login
      user.last_login = new Date();
      await this.userRepository.save(user);

      this.logger.log(`login riuscito: ${user.email}`);

      // genera jwt token per autenticare richieste successive
      const token = this.generateToken(user);

      return {
        access_token: token,
        user: {
          id: user.id,
          email: user.email,
          username: user.username,
        },
      };
    } catch (error) {
      this.logger.error(`errore login: ${error.message}`);
      throw error;
    }
  }

  /**
   * valida jwt token
   * verifica firma e scadenza del token
   * ritorna utente se valido, null se invalido
   */
  async validateToken(token: string): Promise<UserEntity | null> {
    try {
      // verify controlla firma e scadenza automaticamente
      const payload = this.jwtService.verify(token);
      
      // recupera utente dal database usando id nel payload
      const user = await this.userRepository.findOne({
        where: { id: payload.sub },
      });

      return user;
    } catch (error) {
      this.logger.error(`token non valido: ${error.message}`);
      return null;
    }
  }

  /**
   * recupera utente per id
   * usato da jwt strategy per validazione
   */
  async getUserById(userId: string): Promise<UserEntity | null> {
    try {
      const user = await this.userRepository.findOne({
        where: { id: userId },
      });

      return user;
    } catch (error) {
      this.logger.error(`errore recupero utente ${userId}: ${error.message}`);
      return null;
    }
  }

  /**
   * genera jwt token per utente
   * payload contiene id, email, username
   * token firmato con secret key e scade dopo expiration time
   */
  private generateToken(user: UserEntity): string {
    // payload: dati non sensibili inclusi nel token
    // sub (subject): standard jwt per id utente
    const payload = {
      sub: user.id,
      email: user.email,
      username: user.username,
    };

    // firma token con secret key
    // ritorna stringa jwt base64 encoded
    return this.jwtService.sign(payload);
  }
}