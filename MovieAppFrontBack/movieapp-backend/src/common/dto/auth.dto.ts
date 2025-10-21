// DTO PER AUTENTICAZIONE

import { IsEmail, IsString, MinLength, IsOptional } from 'class-validator';

/**
 * DTO: Registrazione nuovo utente
 */
export class RegisterDto {
  @IsEmail({}, { message: 'email non valida' })
  email: string;

  @IsString()
  @MinLength(6, { message: 'password deve essere almeno 6 caratteri' })
  password: string;

  @IsOptional()
  @IsString()
  username?: string;
}

/**
 * DTO: Login utente
 */
export class LoginDto {
  @IsEmail({}, { message: 'email non valida' })
  email: string;

  @IsString()
  @MinLength(6, { message: 'password deve essere almeno 6 caratteri' })
  password: string;
}

/**
 * Response autenticazione con token JWT
 */
export class AuthResponse {
  access_token: string;
  user: {
    id: string;
    email: string;
    username?: string;
    created_at: Date;
  };
}