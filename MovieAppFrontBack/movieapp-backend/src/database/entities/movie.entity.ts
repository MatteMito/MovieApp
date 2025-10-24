import {
  Entity,
  Column,
  PrimaryColumn,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
} from 'typeorm';

/**
 * ENTITY: Movie
 * Contiene SOLO i dati del film (senza dati utente)
 * I dati utente (watched, rating, etc.) sono in UserMovieEntity
 */
@Entity('movies')
@Index(['title', 'year'])
@Index(['tmdb_id'])
@Index(['source'])
@Index(['is_enriched'])
export class MovieEntity {
  // ===== IDENTIFICATORI =====
  
  @PrimaryColumn({ type: 'varchar', length: 255 })
  id: string;

  // ===== DATI BASE =====
  
  @Column({ length: 500 })
  title: string;

  @Column({ nullable: true })
  year?: number;

  @Column({ length: 50, default: 'UNKNOWN' })
  source: string; // IMDB, LETTERBOXD, TMDB, etc.

  // ===== DATI TMDB ARRICCHITI =====
  
  @Column({ nullable: true })
  tmdb_id?: number;

  @Column({ default: false })
  is_enriched: boolean;

  @Column('text', { array: true, default: '{}' })
  genres: string[];

  @Column({ nullable: true, length: 255 })
  director?: string;

  @Column('text', { array: true, nullable: true })
  actors?: string[];

  @Column('text', { nullable: true })
  overview?: string;

  @Column({ nullable: true, length: 500 })
  tagline?: string;

  @Column({ nullable: true })
  runtime?: number;

  // ===== POSTER E IMMAGINI =====
  
  @Column({ nullable: true, length: 500 })
  poster_url?: string;

  @Column({ nullable: true, length: 500 })
  backdrop_url?: string;

  // ===== RATING E POPOLARITÀ TMDB =====
  
  @Column('decimal', { precision: 3, scale: 1, nullable: true })
  tmdb_rating?: number;

  @Column({ nullable: true })
  vote_count?: number;

  @Column('decimal', { precision: 10, scale: 3, nullable: true })
  popularity?: number;

  // ===== DATI PRODUZIONE =====
  
  @Column('bigint', { nullable: true })
  budget?: number;

  @Column('bigint', { nullable: true })
  revenue?: number;

  @Column({ nullable: true, length: 100 })
  status?: string;

  @Column('text', { array: true, default: '{}' })
  production_companies: string[];

  @Column('text', { array: true, default: '{}' })
  production_countries: string[];

  // ===== LINGUE E TITOLO ORIGINALE =====
  
  @Column({ nullable: true, length: 10 })
  original_language?: string;

  @Column({ nullable: true, length: 500 })
  original_title?: string;

  @Column('text', { array: true, default: '{}' })
  spoken_languages: string[];

  // ===== METADATA VARI =====
  
  @Column({ default: false })
  adult: boolean;

  @Column({ nullable: true, length: 500 })
  homepage?: string;

  @Column({ nullable: true, length: 20 })
  imdb_id?: string;

  @Column('text', { array: true, default: '{}' })
  keywords: string[];

  @Column({ nullable: true, length: 20 })
  certification?: string;

  @Column({ nullable: true, length: 500 })
  trailer_url?: string;

  // ===== TIMESTAMP AUTOMATICI =====
  
  @CreateDateColumn()
  created_at: Date;

  @UpdateDateColumn()
  updated_at: Date;
}