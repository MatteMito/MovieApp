// dto per autenticazione

import { IsEmail, IsString, MinLength, IsOptional } from 'class-validator';

// dto per registrazione nuovo utente
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

// dto per login utente
export class LoginDto {
  @IsEmail({}, { message: 'email non valida' })
  email: string;

  @IsString()
  @MinLength(6, { message: 'password deve essere almeno 6 caratteri' })
  password: string;
}

// response autenticazione con token jwt
export class AuthResponse {
  access_token: string;
  user: {
    id: string;
    email: string;
    username?: string;
    created_at: Date;
  };
}