// File: src/common/dto/movie.dto.ts
// AGGIORNATO: rimossi campi utente (ora gestiti da UserMovieEntity)

import {
  IsString,
  IsNumber,
  IsBoolean,
  IsOptional,
  IsArray,
  Min,
  Max,
} from 'class-validator';

/**
 * DTO per creazione film (SENZA dati utente)
 */
export class CreateMovieDto {
  @IsString()
  title: string;

  @IsOptional()
  @IsNumber()
  @Min(1888)
  @Max(2030)
  year?: number;

  @IsOptional()
  @IsString()
  director?: string;

  @IsOptional()
  @IsArray()
  @IsString({ each: true })
  genres?: string[];

  @IsString()
  source: string;

  // ⚠️ RIMOSSI: user_rating, date_rated, is_watched
  // Ora gestiti in UserMovieDto
}

/**
 * DTO per aggiornamento film (SENZA dati utente)
 */
export class UpdateMovieDto {
  @IsOptional()
  @IsString()
  title?: string;

  @IsOptional()
  @IsNumber()
  year?: number;

  @IsOptional()
  @IsString()
  director?: string;

  @IsOptional()
  @IsArray()
  genres?: string[];

  // ⚠️ RIMOSSI: user_rating, date_rated, is_watched
}

/**
 * DTO per enrichment film
 * Include SOLO i dati necessari per identificare e arricchire il film
 */
export class EnrichMovieDto {
  @IsString()
  id: string;

  @IsString()
  title: string;

  @IsOptional()
  @IsNumber()
  year?: number;

  @IsString()
  source: string;

  // ⚠️ RIMOSSI: user_rating, date_rated, is_watched
  // Per il batch upload, questi verranno gestiti separatamente
}

/**
 * DTO per batch upload
 * Ora include SOLO i film da arricchire
 * I dati utente vengono gestiti separatamente nel processo
 */
export class BatchUploadDto {
  @IsArray()
  watchlist: EnrichMovieDto[];

  @IsArray()
  watched: EnrichMovieDto[];
}

/**
 * 🆕 DTO per associare film a utente
 * Contiene i dati specifici dell'utente
 */
export class UserMovieDto {
  @IsString()
  movieId: string;

  @IsString()
  status: 'watched' | 'watchlist';

  @IsOptional()
  @IsNumber()
  @Min(0)
  @Max(10)
  userRating?: number;

  @IsOptional()
  @IsString()
  watchedDate?: string;

  @IsOptional()
  @IsString()
  userReview?: string;

  @IsString()
  source: string;
}

/**
 * 🆕 DTO per batch upload con dati utente
 * Versione completa che include sia i film che i dati utente
 */
export class BatchUploadWithUserDto {
  @IsString()
  userId: string;

  @IsArray()
  watchlist: Array<{
    id: string;
    title: string;
    year?: number;
    source: string;
    userRating?: number;
    userReview?: string;
  }>;

  @IsArray()
  watched: Array<{
    id: string;
    title: string;
    year?: number;
    source: string;
    userRating?: number;
    watchedDate?: string;
    userReview?: string;
  }>;
}