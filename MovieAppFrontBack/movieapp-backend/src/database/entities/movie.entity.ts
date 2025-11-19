import {
  Entity,
  Column,
  PrimaryColumn,
  CreateDateColumn,
  UpdateDateColumn,
  Index,
} from 'typeorm';

// entity per i film nel catalogo
// contiene solo i dati del film, senza dati utente
// i dati utente (watched, rating, etc.) sono in user_movies table
@Entity('movies')
@Index(['title', 'year'])
@Index(['tmdb_id'])
@Index(['source'])
@Index(['is_enriched'])
export class MovieEntity {
    
  @PrimaryColumn({ type: 'varchar', length: 255 })
  id: string; // id composito o da fonte esterna
  
  @Column({ length: 500 })
  title: string;

  @Column({ nullable: true })
  year?: number;

  @Column({ length: 50, default: 'UNKNOWN' })
  source: string; // fonte dati: IMDB, LETTERBOXD, TMDB, etc.
  
  @Column({ nullable: true })
  tmdb_id?: number; // id del film su tmdb

  @Column({ default: false })
  is_enriched: boolean; // flag per sapere se il film è stato arricchito con dati tmdb

  @Column('text', { array: true, default: '{}' })
  genres: string[]; // array di generi

  @Column({ nullable: true, length: 255 })
  director?: string;

  @Column('text', { array: true, nullable: true })
  actors?: string[]; // array dei principali attori

  @Column('text', { nullable: true })
  overview?: string; // trama del film

  @Column({ nullable: true, length: 500 })
  tagline?: string; // slogan del film

  @Column({ nullable: true })
  runtime?: number; // durata in minuti
  
  @Column({ nullable: true, length: 500 })
  poster_url?: string; // url poster tmdb

  @Column({ nullable: true, length: 500 })
  backdrop_url?: string; // url immagine sfondo tmdb
  
  @Column('decimal', { precision: 3, scale: 1, nullable: true })
  tmdb_rating?: number; // rating medio tmdb (0-10)

  @Column({ nullable: true })
  vote_count?: number; // numero di voti su tmdb

  @Column('decimal', { precision: 10, scale: 3, nullable: true })
  popularity?: number; // punteggio popolarità tmdb
  
  @Column('bigint', { nullable: true })
  budget?: number; // budget in dollari

  @Column('bigint', { nullable: true })
  revenue?: number; // incassi in dollari

  @Column({ nullable: true, length: 100 })
  status?: string; // stato: released, post-production, etc.

  @Column('text', { array: true, default: '{}' })
  production_companies: string[]; // case di produzione

  @Column('text', { array: true, default: '{}' })
  production_countries: string[]; // paesi di produzione
  
  @Column({ nullable: true, length: 10 })
  original_language?: string; // codice lingua originale

  @Column({ nullable: true, length: 500 })
  original_title?: string; // titolo originale del film

  @Column('text', { array: true, default: '{}' })
  spoken_languages: string[]; // lingue parlate nel film
  
  @Column({ default: false })
  adult: boolean; // film per adulti

  @Column({ nullable: true, length: 500 })
  homepage?: string; // sito ufficiale del film

  @Column({ nullable: true, length: 20 })
  imdb_id?: string; // id imdb (es: tt1234567)

  @Column('text', { array: true, default: '{}' })
  keywords: string[]; // keywords/tag del film

  @Column({ nullable: true, length: 20 })
  certification?: string; // rating censura (PG-13, R, etc)

  @Column({ nullable: true, length: 500 })
  trailer_url?: string; // url trailer (youtube, etc)
  
  @CreateDateColumn()
  created_at: Date; // data creazione record

  @UpdateDateColumn()
  updated_at: Date; // data ultimo aggiornamento (auto-aggiornato da trigger)
}