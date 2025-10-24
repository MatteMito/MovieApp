// FILE: movieapp-backend/src/common/dto/list.dto.ts
// DTO completi per gestione liste personalizzate

import {
  IsString,
  IsOptional,
  IsBoolean,
  IsArray,
  IsDateString,
  MinLength,
  MaxLength,
  IsUUID,
} from 'class-validator';

/**
 * DTO: Creazione nuova lista
 */
export class CreateListDto {
  @IsUUID()
  user_id: string;

  @IsString()
  @MinLength(1)
  @MaxLength(200)
  name: string;

  @IsOptional()
  @IsString()
  @MaxLength(1000)
  description?: string;

  @IsOptional()
  @IsArray()
  movie_ids?: string[];

  @IsOptional()
  @IsDateString()
  target_date?: string;

  @IsOptional()
  @IsString()
  @MaxLength(50)
  frequency?: string;

  @IsOptional()
  @IsBoolean()
  is_public?: boolean;
}

/**
 * DTO: Aggiornamento lista esistente
 */
export class UpdateListDto {
  @IsOptional()
  @IsString()
  @MinLength(1)
  @MaxLength(200)
  name?: string;

  @IsOptional()
  @IsString()
  @MaxLength(1000)
  description?: string;

  @IsOptional()
  @IsArray()
  movie_ids?: string[];

  @IsOptional()
  @IsDateString()
  target_date?: string;

  @IsOptional()
  @IsString()
  @MaxLength(50)
  frequency?: string;

  @IsOptional()
  @IsBoolean()
  is_public?: boolean;
}

/**
 * DTO: Aggiunta film a lista
 */
export class AddMovieToListDto {
  @IsString()
  movie_id: string;
}

/**
 * DTO: Ricerca film per liste
 */
export class SearchMovieDto {
  @IsOptional()
  @IsString()
  query?: string;

  @IsOptional()
  @IsString()
  director?: string;

  @IsOptional()
  @IsString()
  genre?: string;

  @IsOptional()
  @IsString()
  year?: string;
}