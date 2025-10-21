//data transfer objects per movie

import {
  IsString,
  IsNumber,
  IsBoolean,
  IsOptional,
  IsArray,
  Min,
  Max,
} from 'class-validator';

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

  @IsOptional()
  @IsNumber()
  @Min(0)
  @Max(10)
  user_rating?: number;

  @IsOptional()
  @IsString()
  date_rated?: string;

  @IsBoolean()
  is_watched: boolean;

  @IsString()
  source: string;
}

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

  @IsOptional()
  @IsNumber()
  user_rating?: number;

  @IsOptional()
  @IsString()
  date_rated?: string;

  @IsOptional()
  @IsBoolean()
  is_watched?: boolean;
}

export class EnrichMovieDto {
  @IsString()
  id: string;

  @IsString()
  title: string;

  @IsOptional()
  @IsNumber()
  year?: number;

  @IsOptional()
  @IsNumber()
  user_rating?: number;

  @IsOptional()
  @IsString()
  date_rated?: string;

  @IsBoolean()
  is_watched: boolean;

  @IsString()
  source: string;
}

export class BatchUploadDto {
  @IsArray()
  watchlist: EnrichMovieDto[];

  @IsArray()
  watched: EnrichMovieDto[];
}