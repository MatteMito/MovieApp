// dto completi per gestione liste personalizzate

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

// dto per creazione nuova lista
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
  movie_ids?: string[]; // array di id film da aggiungere alla lista

  @IsOptional()
  @IsDateString()
  target_date?: string; // data target per completare la lista

  @IsOptional()
  @IsString()
  @MaxLength(50)
  frequency?: string; // frequenza di aggiornamento (weekly, monthly, etc)

  @IsOptional()
  @IsBoolean()
  is_public?: boolean; // se la lista è visibile pubblicamente
}

// dto per aggiornamento lista esistente
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

// dto per aggiungere un film a una lista
export class AddMovieToListDto {
  @IsString()
  movie_id: string;
}

// dto per ricerca film con filtri multipli
export class SearchMovieDto {
  @IsOptional()
  @IsString()
  query?: string; // ricerca testuale su titolo

  @IsOptional()
  @IsString()
  director?: string; // filtra per regista

  @IsOptional()
  @IsString()
  genre?: string; // filtra per genere

  @IsOptional()
  @IsString()
  year?: string; // filtra per anno
}