//data transfer objects per gestione film

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
 * dto per creazione film base
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
}

/**
 * dto per aggiornamento film
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
}

/**
 * dto per enrichment film
 * contiene solo dati necessari per identificare e arricchire film
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
}

/**
 * dto per batch upload
 * include solo film da arricchire
 */
export class BatchUploadDto {
  @IsArray()
  watchlist: EnrichMovieDto[];

  @IsArray()
  watched: EnrichMovieDto[];
}

/**
 * dto per associare film a utente
 * contiene dati specifici utente
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
 * dto per batch upload con dati utente
 * versione completa che include sia film che dati utente
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