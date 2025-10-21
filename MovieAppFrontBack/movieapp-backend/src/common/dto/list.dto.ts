// DTO PER LISTE FILM

import {
  IsString,
  IsOptional,
  IsBoolean,
  IsArray,
  IsDateString,
  MinLength,
  MaxLength,
} from 'class-validator';

/**
 * DTO: Creazione nuova lista
 */
export class CreateListDto {
  @IsString()
  @MinLength(1)
  @MaxLength(200)
  name: string;

  @IsOptional()
  @IsString()
  description?: string;

  @IsOptional()
  @IsArray()
  movie_ids?: string[];

  @IsOptional()
  @IsDateString()
  target_date?: string;

  @IsOptional()
  @IsString()
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
  name?: string;

  @IsOptional()
  @IsString()
  description?: string;

  @IsOptional()
  @IsArray()
  movie_ids?: string[];

  @IsOptional()
  @IsDateString()
  target_date?: string;

  @IsOptional()
  @IsString()
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