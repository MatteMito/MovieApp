import {
  IsString,
  IsNumber,
  IsBoolean,
  IsOptional,
  IsArray,
  Min,
  Max,
} from 'class-validator';

// dto per creazione film
export class CreateMovieDto {
  @IsString()
  title: string;

  @IsOptional()
  @IsNumber()
  @Min(1888) // primo film della storia
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
  source: string; // fonte dati (tmdb, imdb, letterboxd, etc)

  // rimossi: user_rating, date_rated, is_watched
  // ora gestiti in user_movies table
}

// dto per aggiornamento film
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

  // rimossi: user_rating, date_rated, is_watched
}

// dto per enrichment film tramite tmdb
// include solo i dati necessari per identificare e arricchire il film
export class EnrichMovieDto {
  @IsString()
  id: string; // id del film nel sistema

  @IsString()
  title: string;

  @IsOptional()
  @IsNumber()
  year?: number;

  @IsString()
  source: string; // fonte originale dei dati

  // rimossi: user_rating, date_rated, is_watched
  // per il batch upload questi vengono gestiti separatamente
}

// dto per batch upload di film
// include solo i film da arricchire, i dati utente vengono gestiti dopo
export class BatchUploadDto {
  @IsArray()
  watchlist: EnrichMovieDto[]; // film nella watchlist

  @IsArray()
  watched: EnrichMovieDto[]; // film già visti
}

// dto per associare un film a un utente
// contiene i dati specifici della relazione utente-film
export class UserMovieDto {
  @IsString()
  movieId: string; // id del film

  @IsString()
  status: 'watched' | 'watchlist'; // stato del film per l'utente

  @IsOptional()
  @IsNumber()
  @Min(0)
  @Max(10)
  userRating?: number; // voto personale 0-10

  @IsOptional()
  @IsString()
  watchedDate?: string; // data in cui l'utente ha visto il film

  @IsOptional()
  @IsString()
  userReview?: string; // recensione personale

  @IsString()
  source: string; // fonte originale dei dati
}

// dto per batch upload completo con dati utente
// versione completa che include sia i film che i dati utente associati
export class BatchUploadWithUserDto {
  @IsString()
  userId: string; // id dell'utente che fa l'upload

  @IsArray()
  watchlist: Array<{
    id: string;
    title: string;
    year?: number;
    source: string;
    userRating?: number; // rating anche per film in watchlist (anticipato)
    userReview?: string;
  }>;

  @IsArray()
  watched: Array<{
    id: string;
    title: string;
    year?: number;
    source: string;
    userRating?: number;
    watchedDate?: string; // data visione
    userReview?: string;
  }>;
}