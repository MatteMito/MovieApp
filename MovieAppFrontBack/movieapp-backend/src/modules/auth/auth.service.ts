//servizio autenticazione con jwt e bcrypt

import { Injectable, Logger, UnauthorizedException } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import * as bcrypt from 'bcrypt';
import { UserEntity } from '../../database/entities/user.entity';

//dto per registrazione e login
export interface RegisterDto {
  email: string;
  password: string;
  username?: string;
}

export interface LoginDto {
  email: string;
  password: string;
}

//response autenticazione
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
    @InjectRepository(UserEntity)
    private userRepository: Repository<UserEntity>,
    private jwtService: JwtService,
  ) {}

  /**
   * registrazione nuovo utente
   * hash password con bcrypt
   */
  async register(dto: RegisterDto): Promise<AuthResponse> {
    try {
      this.logger.log(`📝 tentativo registrazione: ${dto.email}`);

      //verifica se email già esistente
      const existingUser = await this.userRepository.findOne({
        where: { email: dto.email },
      });

      if (existingUser) {
        throw new UnauthorizedException('email già registrata');
      }

      //hash password
      const hashedPassword = await bcrypt.hash(dto.password, 10);

      //crea nuovo utente
      const user = this.userRepository.create({
        email: dto.email,
        password: hashedPassword,
        username: dto.username,
        is_active: true,
      });

      const savedUser = await this.userRepository.save(user);

      this.logger.log(`✅ utente registrato: ${savedUser.email}`);

      //genera jwt token
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
      this.logger.error(`❌ errore registrazione: ${error.message}`);
      throw error;
    }
  }

  /**
   * login utente esistente
   * verifica password con bcrypt
   */
  async login(dto: LoginDto): Promise<AuthResponse> {
    try {
      this.logger.log(`🔐 tentativo login: ${dto.email}`);

      //cerca utente per email
      const user = await this.userRepository.findOne({
        where: { email: dto.email },
      });

      if (!user) {
        throw new UnauthorizedException('credenziali non valide');
      }

      //verifica password
      const isPasswordValid = await bcrypt.compare(dto.password, user.password);

      if (!isPasswordValid) {
        throw new UnauthorizedException('credenziali non valide');
      }

      //aggiorna last login
      user.last_login = new Date();
      await this.userRepository.save(user);

      this.logger.log(`✅ login riuscito: ${user.email}`);

      //genera jwt token
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
      this.logger.error(`❌ errore login: ${error.message}`);
      throw error;
    }
  }

  /**
   * valida jwt token
   */
  async validateToken(token: string): Promise<UserEntity | null> {
    try {
      const payload = this.jwtService.verify(token);
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
   */
  private generateToken(user: UserEntity): string {
    const payload = {
      sub: user.id,
      email: user.email,
      username: user.username,
    };

    return this.jwtService.sign(payload);
  }
}